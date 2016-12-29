package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateKeyPair
import io.slychat.messenger.core.crypto.identityKeyFingerprint
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.crypto.signal.InvalidPreKeyIdException
import io.slychat.messenger.core.crypto.signal.InvalidSignedPreKeyIdException
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.EventLogService
import io.slychat.messenger.services.crypto.DecryptionResult
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.whispersystems.libsignal.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageReceiverImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    private data class GeneratedTextMessages(
        val packages: List<Package>,
        val wrappers: List<SlyMessage.Text>,
        val decryptedResults: List<DecryptionResult>
    )

    private class TestException : Exception("Test exc")

    private val messageProcessor: MessageProcessor = mock()
    private val packageQueuePersistenceManager: PackageQueuePersistenceManager = mock()
    private val messageCipherService: MessageCipherService = mock()
    private val eventLogService: EventLogService = mock()

    private fun createTextMessage(message: String, group: String? = null): SlyMessage.Text {
        val groupId = group?.let(::GroupId)
        return SlyMessage.Text(TextMessage(MessageId(randomMessageId()), currentTimestamp(), message, groupId, 0))
    }

    private fun createPackage(from: UserId, payload: ByteArray): Package {
        val mid = randomUUID()

        val objectMapper = ObjectMapper()

        val enc = EncryptedPackagePayloadV0(false, payload)

        return Package(
            PackageId(SlyAddress(from, 1), mid),
            currentTimestamp(),
            objectMapper.writeValueAsString(enc)
        )
    }

    private fun createPackage(from: UserId, textMessage: TextMessage): Package {
        val objectMapper = ObjectMapper()

        val payload = objectMapper.writeValueAsBytes(textMessage)

        return createPackage(from, payload)
    }

    private fun createPackage(from: UserId, message: String): Package {
        val m = createTextMessage(message).m
        return createPackage(from, m)
    }

    private fun generateMessages(from: UserId, vararg messages: String): GeneratedTextMessages {
        val objectMapper = ObjectMapper()

        val packages = ArrayList<Package>()
        val wrappers = ArrayList<SlyMessage.Text>()
        val results = ArrayList<DecryptionResult>()

        messages.forEach {
            val wrapped = createTextMessage(it)
            val pkg = createPackage(from, wrapped.m)
            val result = DecryptionResult(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

            wrappers.add(wrapped)
            packages.add(pkg)
            results.add(result)
        }

        return GeneratedTextMessages(packages, wrappers, results)
    }

    fun createReceiver(): MessageReceiverImpl {
        whenever(packageQueuePersistenceManager.removeFromQueue(any<Collection<PackageId>>())).thenResolve(Unit)
        whenever(packageQueuePersistenceManager.removeFromQueue(any<UserId>(), any())).thenResolve(Unit)
        whenever(packageQueuePersistenceManager.addToQueue(any<Collection<Package>>())).thenResolve(Unit)
        whenever(messageProcessor.processMessage(any(), any())).thenResolve(Unit)
        whenever(eventLogService.addEvent(any())).thenResolveUnit()

        return MessageReceiverImpl(
            messageProcessor,
            packageQueuePersistenceManager,
            messageCipherService,
            eventLogService
        )
    }

    private fun setRandomDecryptionResult() {
        val objectMapper = ObjectMapper()
        val wrapped = createTextMessage(randomMessageText())
        val messageId = randomMessageId()

        val result = DecryptionResult(messageId, objectMapper.writeValueAsBytes(wrapped))
        setDecryptionResult(result)
    }

    private fun setDecryptionResult(result: DecryptionResult) {
        whenever(messageCipherService.decrypt(any(), any())).thenResolve(result)
    }

    @Test
    fun `it should notifier the submitter once the packages have been successfully queued`() {
        val receiver = createReceiver()

        val packages = listOf(
            createPackage(UserId(1), "message")
        )

        whenever(packageQueuePersistenceManager.addToQueue(any<Collection<Package>>())).thenResolve(Unit)

        setRandomDecryptionResult()

        receiver.processPackages(packages).get()
    }

    @Test
    fun `it should propagate failures to the submitter when package queue fails`() {
        val receiver = createReceiver()

        val packages = listOf(
            createPackage(UserId(1), "message")
        )

        whenever(packageQueuePersistenceManager.addToQueue(any<Collection<Package>>())).thenReject(TestException())

        assertFailsWith(TestException::class) {
            receiver.processPackages(packages).get()
        }
    }

    @Test
    fun `it should send messages to be decrypted once persistence is complete`() {
        val receiver = createReceiver()

        val pkg = createPackage(UserId(1), "test")

        setRandomDecryptionResult()

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

        val result = DecryptionResult(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

        setDecryptionResult(result)

        receiver.processPackages(listOf(pkg))

        val captor = argumentCaptor<SlyMessage>()
        verify(messageProcessor).processMessage(eq(from), capture(captor))

        assertEquals(wrapped, captor.value, "Deserialized message doesn't match")
    }

    @Test
    fun `it should discard messages which fail deserialization after decryption`() {
        val receiver = createReceiver()

        val from = UserId(1)
        val pkg = createPackage(from, "invalid")

        val result = DecryptionResult(pkg.id.messageId, "invalid".toByteArray())

        setDecryptionResult(result)

        receiver.processPackages(listOf(pkg))

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

        val result = generated.decryptedResults[0]

        setDecryptionResult(result)

        receiver.processPackages(generated.packages)

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

        val result = DecryptionResult(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

        setDecryptionResult(result)

        receiver.processPackages(listOf(pkg))

        verify(packageQueuePersistenceManager).removeFromQueue(from, listOf(pkg.id.messageId))
    }

    //TODO not sure what to do on failure here... since certain things can fail due to network issues we can't outright
    //delete packages on failure

    @Test
    fun `it should fetch all pending packages on initialization`() {
        val receiver = createReceiver()

        whenever(packageQueuePersistenceManager.getQueuedPackages()).thenResolve(emptyList())

        receiver.init()

        verify(packageQueuePersistenceManager).getQueuedPackages()
    }

    private fun testDecryptionFailureLog(cause: Exception, createData: (SlyAddress) -> SecurityEventData) {
        val receiver = createReceiver()

        val sender = randomSlyAddress()

        val objectMapper = ObjectMapper()

        val pkg = Package(
            PackageId(sender, randomUUID()),
            currentTimestamp(),
            objectMapper.writeValueAsString(EncryptedPackagePayloadV0(false, ByteArray(0)))
        )

        whenever(messageCipherService.decrypt(any(), any())).thenReject(cause)

        receiver.processPackages(listOf(pkg))

        val data = createData(sender)

        verify(eventLogService).addEvent(capture {
            assertThat(it.data).apply {
                `as`("Should log valid data")
                isInstanceOf(SecurityEventData::class.java)
                isEqualTo(data)
            }
        })
    }

    @Test
    fun `it should log decryption failure for DuplicateMessageException`() {
        testDecryptionFailureLog(DuplicateMessageException("dup")) {
            SecurityEventData.DuplicateMessage(it)
        }
    }

    @Test
    fun `it should log decryption failure for NoSessionException`() {
        testDecryptionFailureLog(NoSessionException("no session")) {
            SecurityEventData.NoSession(it)
        }
    }

    @Test
    fun `it should log decryption failure for InvalidMessageException`() {
        val issue = "invalid message"

        testDecryptionFailureLog(InvalidMessageException(issue)) {
            SecurityEventData.InvalidMessage(it, issue)
        }
    }

    @Test
    fun `it should log decryption failure for InvalidPreKeyIdException`() {
        val id = randomInt()

        testDecryptionFailureLog(InvalidPreKeyIdException(id)) {
            SecurityEventData.InvalidPreKeyId(it, id)
        }
    }

    @Test
    fun `it should log decryption failure for InvalidSignedPreKeyIdException`() {
        val id = randomInt()

        testDecryptionFailureLog(InvalidSignedPreKeyIdException(id)) {
            SecurityEventData.InvalidSignedPreKeyId(it, id)
        }
    }

    @Test
    fun `it should log decryption failure for InvalidKeyException`() {
        val issue = "invalid signature on device key!"

        testDecryptionFailureLog(InvalidKeyException(issue)) {
            SecurityEventData.InvalidKey(it, issue)
        }
    }

    @Test
    fun `it should log decryption failure for UntrustedIdentityException`() {
        val publicKey = generateKeyPair().publicKey

        testDecryptionFailureLog(UntrustedIdentityException("name", publicKey)) {
            SecurityEventData.UntrustedIdentity(it, identityKeyFingerprint(publicKey))
        }
    }

    @Test
    fun `it should emit an event once the process queue has become empty`() {
        val receiver = createReceiver()

        val testSubscriber = receiver.queueIsEmpty.testSubscriber()

        val from = UserId(1)
        val objectMapper = ObjectMapper()
        val wrapped = createTextMessage("test")
        val pkg = createPackage(from, wrapped.m)

        val result = DecryptionResult(pkg.id.messageId, objectMapper.writeValueAsBytes(wrapped))

        setDecryptionResult(result)

        receiver.processPackages(listOf(pkg))

        assertThat(testSubscriber.onNextEvents).apply {
            describedAs("Should emit an event once the queue becomes empty")
            hasSize(1)
        }
    }
}