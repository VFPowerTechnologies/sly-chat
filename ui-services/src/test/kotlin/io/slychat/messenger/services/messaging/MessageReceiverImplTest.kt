package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageDecryptionResult
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.cond
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenReturn
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageReceiverImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    data class GeneratedTextMessages(
        val packages: List<Package>,
        val wrappers: List<TextMessageWrapper>,
        val decryptedResults: List<MessageDecryptionResult.Success>
    )

    class TestException : Exception("Test exc")

    val messageProcessor: MessageProcessor = mock()
    val packageQueuePersistenceManager: PackageQueuePersistenceManager = mock()
    val messageCipherService: MessageCipherService = mock()

    val decryptionResults: PublishSubject<DecryptionResult> = PublishSubject.create()

    fun createTextMessage(message: String, group: String? = null): TextMessageWrapper {
        val groupId = group?.let { GroupId(it) }
        return TextMessageWrapper(TextMessage(currentTimestamp(), message, groupId))
    }

    fun createPackage(from: UserId, payload: ByteArray): Package {
        val mid = randomUUID()

        val objectMapper = ObjectMapper()

        val enc = EncryptedPackagePayloadV0(false, payload)

        return Package(
            PackageId(SlyAddress(from, 1), mid),
            currentTimestamp(),
            objectMapper.writeValueAsString(enc)
        )
    }

    fun createPackage(from: UserId, textMessage: TextMessage): Package {
        val objectMapper = ObjectMapper()

        val payload = objectMapper.writeValueAsBytes(textMessage)

        return createPackage(from, payload)

    }

    fun createPackage(from: UserId, message: String): Package {
        val m = createTextMessage(message).m
        return createPackage(from, m)
    }

    fun generateMessages(from: UserId, vararg messages: String): GeneratedTextMessages {
        val objectMapper = ObjectMapper()

        val packages = ArrayList<Package>()
        val wrappers = ArrayList<TextMessageWrapper>()
        val results = ArrayList<MessageDecryptionResult.Success>()

        messages.forEach {
            val wrapped = createTextMessage(it)
            val pkg = createPackage(from, wrapped.m)
            val result = MessageDecryptionResult.Success(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

            wrappers.add(wrapped)
            packages.add(pkg)
            results.add(result)
        }

        return GeneratedTextMessages(packages, wrappers, results)
    }

    fun createReceiver(): MessageReceiverImpl {
        val scheduler = Schedulers.immediate()

        whenever(messageCipherService.decryptedMessages).thenReturn(decryptionResults)
        whenever(packageQueuePersistenceManager.removeFromQueue(any<Collection<PackageId>>())).thenReturn(Unit)
        whenever(packageQueuePersistenceManager.removeFromQueue(any<UserId>(), any())).thenReturn(Unit)
        whenever(packageQueuePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(Unit)
        whenever(messageProcessor.processMessage(any(), any())).thenReturn(Unit)


        return MessageReceiverImpl(
            scheduler,
            messageProcessor,
            packageQueuePersistenceManager,
            messageCipherService
        )
    }

    @Test
    fun `it should notifier the submitter once the packages have been successfully queued`() {
        val receiver = createReceiver()

        val packages = listOf(
            createPackage(UserId(1), "message")
        )

        whenever(packageQueuePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(Unit)

        receiver.processPackages(packages).get()
    }

    @Test
    fun `it should propagate failures to the submitter when package queue fails`() {
        val receiver = createReceiver()

        val packages = listOf(
            createPackage(UserId(1), "message")
        )

        whenever(packageQueuePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(TestException())

        assertFailsWith(TestException::class) {
            receiver.processPackages(packages).get()
        }
    }

    @Test
    fun `it should send messages to be decrypted once persistence is complete`() {
        val receiver = createReceiver()

        val pkg = createPackage(UserId(1), "test")

        receiver.processPackages(listOf(pkg))

        verify(messageCipherService).decrypt(eq(pkg.id.address), any())
    }

    @Test
    fun `it should remove packages from queue if they fail deserialization`() {
        val receiver = createReceiver()

        val id = PackageId(SlyAddress(UserId(1), 1), randomUUID())

        val pkg = Package(
            id,
            currentTimestamp(),
            "payload"
        )

        receiver.processPackages(listOf(pkg))

        val captor = argumentCaptor<Collection<PackageId>>()
        verify(packageQueuePersistenceManager).removeFromQueue(capture(captor))

        assertThat(captor.value)
            .`as`("Discarded packages")
            .hasSize(1)
            .haveExactly(1, cond("PackageId") { it == id })
    }

    @Test
    fun `it should send the decrypted package to the message processor`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val objectMapper = ObjectMapper()
        val wrapped = createTextMessage("test")
        val pkg = createPackage(from, wrapped.m)

        receiver.processPackages(listOf(pkg))

        val result = MessageDecryptionResult.Success(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

        decryptionResults.onNext(DecryptionResult(from, result))

        val captor = argumentCaptor<SlyMessageWrapper>()
        verify(messageProcessor).processMessage(eq(from), capture(captor))

        assertEquals(SlyMessageWrapper(pkg.id.messageId, wrapped), captor.value, "Deserialized message doesn't match")
    }

    @Test
    fun `it should discard messages which fail deserialization after decryption`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val pkg = createPackage(from, "invalid")

        receiver.processPackages(listOf(pkg))

        val result = MessageDecryptionResult.Success(pkg.id.messageId, "invalid".toByteArray())

        decryptionResults.onNext(DecryptionResult(from, result))

        val captor = argumentCaptor<Collection<String>>()
        verify(packageQueuePersistenceManager).removeFromQueue(eq(from), capture(captor))

        assertThat(captor.value)
            .containsOnly(pkg.id.messageId)
            .`as`("Removed packages")
    }

    //TODO test to make sure next message is processed after failed deserialization

    @Test
    fun `it should process the next queued message after the current message has been processed`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val generated = generateMessages(from, "1", "2")
        val address = generated.packages[0].id.address

        receiver.processPackages(generated.packages)

        val result = generated.decryptedResults[0]

        decryptionResults.onNext(DecryptionResult(from, result))

        val captor = argumentCaptor<EncryptedMessageInfo>()

        verify(messageCipherService, times(2)).decrypt(eq(address), capture(captor))

        val invocations = captor.allValues
        assertEquals(2, invocations.size, "decrypt() not invoked twice")

        val value = invocations[1]

        assertEquals(generated.packages[1].id.messageId, value.messageId, "Message IDs don't match")
    }

    @Test
    fun `it should remove the corresponding message package from the queue after processing`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val objectMapper = ObjectMapper()
        val wrapped = createTextMessage("test")
        val pkg = createPackage(from, wrapped.m)

        receiver.processPackages(listOf(pkg))

        val result = MessageDecryptionResult.Success(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

        decryptionResults.onNext(DecryptionResult(from, result))

        verify(packageQueuePersistenceManager).removeFromQueue(from, listOf(pkg.id.messageId))
    }

    //TODO not sure what to do on failure here... since certain things can fail due to network issues we can't outright
    //delete packages on failure

    @Test
    fun `it should fetch all pending packages on initialization`() {
        val receiver = createReceiver()

        whenever(packageQueuePersistenceManager.getQueuedPackages()).thenReturn(emptyList())

        receiver.init()

        verify(packageQueuePersistenceManager).getQueuedPackages()
    }

    @Test
    fun `it should proxy new messages from MessageProcessor`() {
        val subject = PublishSubject.create<MessageBundle>()
        whenever(messageProcessor.newMessages).thenReturn(subject)

        val receiver = createReceiver()

        val testSubscriber = receiver.newMessages.testSubscriber()

        val bundle = MessageBundle(UserId(1), listOf(
            MessageInfo.newReceived("m", currentTimestamp())
        ))

        subject.onNext(bundle)

        val bundles = testSubscriber.onNextEvents

        assertThat(bundles)
            .containsOnlyElementsOf(listOf(bundle))
            .`as`("Received bundles")
    }

    @Test
    fun `it should proxy group events from MessageProcessor`() {
        val subject = PublishSubject.create<GroupEvent>()
        whenever(messageProcessor.groupEvents).thenReturn(subject)

        val receiver = createReceiver()

        val testSubscriber = receiver.groupEvents.testSubscriber()

        val ev = GroupEvent.Joined(randomGroupId(), randomUserIds())
        subject.onNext(ev)

        val events = testSubscriber.onNextEvents

        assertThat(events).apply {
            `as`("Received group events")
            containsOnly(ev)
        }
    }
}