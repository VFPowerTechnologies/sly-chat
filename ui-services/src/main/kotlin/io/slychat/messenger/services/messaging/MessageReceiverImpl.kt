package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.identityKeyFingerprint
import io.slychat.messenger.core.crypto.signal.InvalidPreKeyIdException
import io.slychat.messenger.core.crypto.signal.InvalidSignedPreKeyIdException
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.EventLogService
import io.slychat.messenger.services.crypto.DecryptionResult
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.deserializeEncryptedPackagePayload
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import org.whispersystems.libsignal.*
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class MessageReceiverImpl(
    private val messageProcessor: MessageProcessor,
    private val packageQueuePersistenceManager: PackageQueuePersistenceManager,
    private val messageCipherService: MessageCipherService,
    private val eventLogService: EventLogService
) : MessageReceiver {
    private data class QueuedReceivedMessage(val from: SlyAddress, val encryptedMessage: EncryptedMessageInfo)

    private val log = LoggerFactory.getLogger(javaClass)

    private val receivedMessageQueue = ArrayDeque<QueuedReceivedMessage>()
    private var currentReceivedMessage: QueuedReceivedMessage? = null

    private val queueIsEmptySubject = PublishSubject.create<Unit>()

    override val queueIsEmpty: Observable<Unit>
        get() = queueIsEmptySubject

    private fun initializeReceiveQueue() {
        packageQueuePersistenceManager.getQueuedPackages() successUi { packages ->
            addPackagesToReceivedQueue(packages)
        }
    }

    private fun logDecryptionFailureEvent(address: SlyAddress, cause: Exception) {
        val data = when (cause) {
            is DuplicateMessageException -> SecurityEventData.DuplicateMessage(address)
            is NoSessionException -> SecurityEventData.NoSession(address)
            is InvalidMessageException -> SecurityEventData.InvalidMessage(address, cause.message ?: "no information available")
            is InvalidPreKeyIdException -> SecurityEventData.InvalidPreKeyId(address, cause.id)
            is InvalidSignedPreKeyIdException -> SecurityEventData.InvalidSignedPreKeyId(address, cause.id)
            is InvalidKeyException -> SecurityEventData.InvalidKey(address, cause.message ?: "no information available")
            is UntrustedIdentityException -> SecurityEventData.UntrustedIdentity(address, identityKeyFingerprint(cause.untrustedIdentity))
            else -> return
        }

        val event = LogEvent.Security(LogTarget.Conversation(address.id.toConversationId()), currentTimestamp(), data)
        eventLogService.addEvent(event)
    }

    private fun handleFailedDecryptionResult(address: SlyAddress, packageMessageId: String, cause: Exception) {
        val userId = address.id

        log.warn("Unable to decrypt message from {}: {}", userId, cause.message, cause)

        packageQueuePersistenceManager.removeFromQueue(userId, listOf(packageMessageId)) fail { e ->
            log.warn("Unable to remove failed decryption packages from queue: {}", e.message, e)
        }

        logDecryptionFailureEvent(address, cause)

        nextReceiveMessage()
    }

    private fun handleSuccessfulDecryptionResult(userId: UserId, result: DecryptionResult) {
        val objectMapper = ObjectMapper()

        val m = try {
            objectMapper.readValue(result.data, SlyMessage::class.java)
        }
        catch (e: JsonParseException) {
            log.warn("Unable to deserialize message from {}: {}", userId, e.message, e)
            null
        }

        if (m == null) {
            packageQueuePersistenceManager.removeFromQueue(userId, listOf(result.messageId)) fail { e ->
                log.error("Unable to remove packages from queue: {}", e.message, e)
            }

            nextReceiveMessage()
            return
        }
        else {
            messageProcessor.processMessage(userId, m) bind {
                packageQueuePersistenceManager.removeFromQueue(userId, listOf(result.messageId)) successUi {
                    nextReceiveMessage()
                }
            } failUi { e ->
                log.error("Message processing failed: {}", e.message, e)
                //FIXME not really sure what else I should do here
                nextReceiveMessage()
            }
        }
    }

    private fun nextReceiveMessage() {
        currentReceivedMessage = null
        receivedMessageQueue.pop()
        processReceivedMessageQueue()
    }

    private fun processReceivedMessageQueue() {
        if (currentReceivedMessage != null)
            return

        if (receivedMessageQueue.isEmpty()) {
            log.debug("No more received messages")
            queueIsEmptySubject.onNext(Unit)
            return
        }

        val message = receivedMessageQueue.first

        messageCipherService.decrypt(message.from, message.encryptedMessage) successUi {
            handleSuccessfulDecryptionResult(message.from.id, it)
        } failUi {
            handleFailedDecryptionResult(message.from, message.encryptedMessage.messageId, it)
        }

        currentReceivedMessage = message
    }

    private fun addPackagesToReceivedQueue(packages: List<Package>) {
        val byTimestamp = packages.sortedBy { it.timestamp }

        val failures = ArrayList<PackageId>()

        //TODO can group adjacent messages based on address
        byTimestamp.forEach { pkg ->
            try {
                val payload = deserializeEncryptedPackagePayload(pkg.payload)
                val messageInfo = EncryptedMessageInfo(pkg.id.messageId, payload)
                receivedMessageQueue.add(QueuedReceivedMessage(pkg.id.address, messageInfo))
            }
            catch (e: Exception) {
                log.warn("Unable to decrypt message <<{}>> from {}: {}", pkg.id.messageId, pkg.id.address.asString(), e.message, e)
                failures.add(pkg.id)
            }
        }

        if (failures.isNotEmpty()) {
            packageQueuePersistenceManager.removeFromQueue(failures).fail { e ->
                log.warn("Unable to remove failed deserialized packages from queue: {}", e.message, e)
            }
        }

        processReceivedMessageQueue()
    }

    override fun processPackages(packages: List<Package>): Promise<Unit, Exception> {
        return packageQueuePersistenceManager.addToQueue(packages) successUi {
            addPackagesToReceivedQueue(packages)
        }
    }

    override fun init() {
        initializeReceiveQueue()
    }

    override fun shutdown() {
        receivedMessageQueue.clear()
    }
}