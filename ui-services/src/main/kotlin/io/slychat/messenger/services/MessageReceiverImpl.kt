package io.slychat.messenger.services

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageDecryptionResult
import io.slychat.messenger.services.crypto.deserializeEncryptedPackagePayload
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subscriptions.CompositeSubscription
import java.util.*

class MessageReceiverImpl(
    scheduler: Scheduler,
    private val messageProcessorService: MessageProcessorService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val messageCipherService: MessageCipherService
) : MessageReceiver {
    private data class QueuedReceivedMessage(val from: SlyAddress, val encryptedMessages: EncryptedMessageInfo)

    private val log = LoggerFactory.getLogger(javaClass)

    private val receivedMessageQueue = ArrayDeque<QueuedReceivedMessage>()
    private var currentReceivedMessage: QueuedReceivedMessage? = null

    private val subscriptions = CompositeSubscription()

    override val newMessages: Observable<MessageBundle>
        get() = messageProcessorService.newMessages

    init {
        subscriptions.add(messageCipherService.decryptedMessages.observeOn(scheduler).subscribe {
            processDecryptionResult(it.userId, it.result)
        })
    }

    private fun initializeReceiveQueue() {
        messagePersistenceManager.getQueuedPackages() successUi { packages ->
            addPackagesToReceivedQueue(packages)
        }
    }

    private fun handleFailedDecryptionResult(userId: UserId, result: MessageDecryptionResult.Failure) {
        log.warn("Unable to decrypt message for {}", userId)
         log.warn("Message decryption failure: {}", result.cause.message, result.cause)

        messagePersistenceManager.removeFromQueue(userId, listOf(result.messageId)) fail { e ->
            log.warn("Unable to remove failed decryption packages from queue: {}", e.message, e)
        }

        nextReceiveMessage()
    }

    private fun handleSuccessfulDecryptionResult(userId: UserId, result: MessageDecryptionResult.Success) {
        val objectMapper = ObjectMapper()

        val m = try {
            val m = objectMapper.readValue(result.data, SlyMessage::class.java)
            SlyMessageWrapper(result.messageId, m)
        }
        catch (e: JsonParseException) {
            log.warn("Unable to deserialize message from {}: {}", userId, e.message, e)
            null
        }

        if (m == null) {
            messagePersistenceManager.removeFromQueue(userId, listOf(result.messageId)) fail { e ->
                log.error("Unable to remove packages from queue: {}", e.message, e)
            }

            nextReceiveMessage()
            return
        }
        else {
            messageProcessorService.processMessages(userId, listOf(m)) successUi {
                nextReceiveMessage()
            } failUi { e ->
                log.error("Message processing failed: {}", e.message, e)
                //FIXME not really sure what else I should do here
                nextReceiveMessage()
            }
        }
    }

    private fun processDecryptionResult(userId: UserId, result: MessageDecryptionResult) {
        when (result) {
            is MessageDecryptionResult.Failure -> handleFailedDecryptionResult(userId, result)
            is MessageDecryptionResult.Success -> handleSuccessfulDecryptionResult(userId, result)
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
            return
        }

        val message = receivedMessageQueue.first

        messageCipherService.decrypt(message.from, message.encryptedMessages)

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
            messagePersistenceManager.removeFromQueue(failures).fail { e ->
                log.warn("Unable to remove failed deserialized packages from queue: {}", e.message, e)
            }
        }

        processReceivedMessageQueue()
    }

    override fun processPackages(packages: List<Package>): Promise<Unit, Exception> {
        return messagePersistenceManager.addToQueue(packages) successUi {
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