package io.slychat.messenger.services

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageListDecryptionResult
import io.slychat.messenger.services.crypto.deserializeEncryptedPackagePayload
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

data class QueuedReceivedMessage(val from: SlyAddress, val encryptedMessages: List<EncryptedMessageInfo>)

interface MessageReceiver {
    val newMessages: Observable<MessageBundle>

    /** Promise completes once the packages have been written to disk. */
    fun processPackages(packages: List<Package>): Promise<Unit, Exception>

    fun shutdown()
    fun init()
}

class MessageReceiverImpl(
    scheduler: Scheduler,
    private val messageProcessorService: MessageProcessorService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val messageCipherService: MessageCipherService
) : MessageReceiver {
    private val log = LoggerFactory.getLogger(javaClass)

    private val receivedMessageQueue = ArrayDeque<QueuedReceivedMessage>()
    private var currentReceivedMessage: QueuedReceivedMessage? = null

    private val subscriptions = CompositeSubscription()

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    override val newMessages: Observable<MessageBundle> = newMessagesSubject

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

    private fun handleFailedDecryptionResults(userId: UserId, result: MessageListDecryptionResult) {
        if (result.failed.isEmpty())
            return

        log.warn("Unable to decrypt {} messages for {}", result.failed.size, userId)
        result.failed.forEach { log.warn("Message decryption failure: {}", it.result.cause.message, it.result.cause) }

        messagePersistenceManager.removeFromQueue(userId, result.failed.map { it.messageId }) fail { e ->
            log.warn("Unable to remove failed decryption packages from queue: {}", e.message, e)
        }
    }

    private fun processDecryptionResult(userId: UserId, result: MessageListDecryptionResult) {
        handleFailedDecryptionResults(userId, result)

        val messages = result.succeeded
        if (messages.isEmpty()) {
            nextReceiveMessage()
            return
        }

        val objectMapper = ObjectMapper()

        val toRemove = ArrayList<String>()
        val deserialized = ArrayList<SlyMessageWrapper>()

        messages.forEach {
            try {
                val m = objectMapper.readValue(it.result, SlyMessage::class.java)
                deserialized.add(SlyMessageWrapper(it.messageId, m))
            }
            catch (e: JsonParseException) {
                toRemove.add(it.messageId)
            }
        }

        if (deserialized.isNotEmpty()) {
            messageProcessorService.processMessages(userId, deserialized) successUi {
                nextReceiveMessage()
            } failUi { e ->
                log.error("Message processing failed: {}", e.message, e)
                //FIXME not really sure what else I should do here
                nextReceiveMessage()
            }
        }

        if (toRemove.isNotEmpty()) {
            messagePersistenceManager.removeFromQueue(userId, toRemove) fail { e ->
                log.error("Unable to remove packages from queue: {}", e.message, e)
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
                receivedMessageQueue.add(QueuedReceivedMessage(pkg.id.address, listOf(messageInfo)))
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