package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageDecryptionResult
import io.slychat.messenger.services.crypto.MessageListDecryptionResult
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.cond
import io.slychat.messenger.testutils.thenReturn
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import kotlin.test.assertFailsWith

class MessageReceiverImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    class TestException : Exception("Test exc")

    val messageProcessService: MessageProcessorService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
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

    fun createReceiver(): MessageReceiverImpl {
        val scheduler = Schedulers.immediate()

        whenever(messageCipherService.decryptedMessages).thenReturn(decryptionResults)
        whenever(messagePersistenceManager.removeFromQueue(any<Collection<PackageId>>())).thenReturn(Unit)
        whenever(messagePersistenceManager.removeFromQueue(any<UserId>(), any())).thenReturn(Unit)
        whenever(messagePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(Unit)

        return MessageReceiverImpl(
            scheduler,
            messageProcessService,
            messagePersistenceManager,
            messageCipherService
        )
    }

    @Test
    fun `it should notifier the submitter once the packages have been successfully queued`() {
        val receiver = createReceiver()

        val packages = listOf(
            createPackage(UserId(1), "message")
        )

        whenever(messagePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(Unit)

        receiver.processPackages(packages).get()
    }

    @Test
    fun `it should propagate failures to the submitter when package queue fails`() {
        val receiver = createReceiver()

        val packages = listOf(
            createPackage(UserId(1), "message")
        )

        whenever(messagePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(TestException())

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
        verify(messagePersistenceManager).removeFromQueue(capture(captor))

        assertThat(captor.value)
            .`as`("Discarded packages")
            .hasSize(1)
            .haveExactly(1, cond("PackageId") { it == id })
    }

    //TODO test deserialization failure handling for the decrypted results as well
    @Test
    fun `it should send the decrypted package to the message processor`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val objectMapper = ObjectMapper()
        val wrapped = createTextMessage("test")
        val pkg = createPackage(from, wrapped.m)

        receiver.processPackages(listOf(pkg))

        val succeeded = listOf(
            MessageDecryptionResult(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))
        )

        val result = DecryptionResult(from, MessageListDecryptionResult(succeeded, emptyList()))
        decryptionResults.onNext(result)

        val captor = argumentCaptor<List<SlyMessageWrapper>>()
        verify(messageProcessService).processMessages(capture(captor))

        assertThat(captor.value)
            .containsOnly(SlyMessageWrapper(from, pkg.id.messageId, wrapped))
            .`as`("Deserialized messages")
    }

    @Test
    fun `it should discard messages which fail deserialization after decryption`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val pkg = createPackage(from, "invalid")

        receiver.processPackages(listOf(pkg))

        val succeeded = listOf(
            MessageDecryptionResult(pkg.id.messageId, "invalid".toByteArray())
        )

        val result = DecryptionResult(from, MessageListDecryptionResult(succeeded, emptyList()))
        decryptionResults.onNext(result)

        val captor = argumentCaptor<Collection<String>>()
        verify(messagePersistenceManager).removeFromQueue(eq(from), capture(captor))

        assertThat(captor.value)
            .containsOnly(pkg.id.messageId)
            .`as`("Removed packages")
    }
}