package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.services.RelayClientManager
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

/**
 * Handles:
 *
 * Preprocessing received packages by filtering out blocked users for MessageReceiver.
 *
 * Creating and serializing the proper SlyMessage objects for MessageSender.
 * Marking sent messages as delivered.
 */
class MessengerServiceImpl(
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val relayClientManager: RelayClientManager,
    private val messageSender: MessageSender,
    private val messageReceiver: MessageReceiver,
    private val selfId: UserId
) : MessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()

    override val newMessages: Observable<MessageBundle>
        get() = messageReceiver.newMessages

    private val messageUpdatesSubject = PublishSubject.create<MessageBundle>()
    override val messageUpdates: Observable<MessageBundle> = messageUpdatesSubject

    private val subscriptions = CompositeSubscription()

    init {
        //FIXME
        subscriptions.add(contactsService.contactEvents.subscribe { onContactEvent(it) })

        subscriptions.add(relayClientManager.events.subscribe { onRelayEvent(it) })

        subscriptions.add(messageSender.messageSent.subscribe { onMessageSent(it) })
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun onMessageSent(metadata: MessageMetadata) {
        when (metadata.category) {
            MessageCategory.TEXT_SINGLE -> processSingleUpdate(metadata)
            MessageCategory.TEXT_GROUP -> processGroupUpdate(metadata)
            MessageCategory.OTHER -> {}
        }
    }

    private fun processSingleUpdate(metadata: MessageMetadata) {
        messagePersistenceManager.markMessageAsDelivered(metadata.userId, metadata.messageId) successUi { messageInfo ->
            val bundle = MessageBundle(metadata.userId, listOf(messageInfo))
            messageUpdatesSubject.onNext(bundle)
        }
    }

    private fun processGroupUpdate(metadata: MessageMetadata) {
        //can't be null due to constructor checks
        val groupId = metadata.groupId!!

        groupPersistenceManager.markMessageAsDelivered(groupId, metadata.messageId) successUi { messageInfo ->
            val bundle = MessageBundle(metadata.userId, groupId, listOf(messageInfo))
            messageUpdatesSubject.onNext(bundle)
        }
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ReceivedMessage ->
                handleReceivedMessage(event)
        }
    }

    private fun handleReceivedMessage(event: ReceivedMessage) {
        val timestamp = currentTimestamp()
        //XXX this is kinda hacky...
        //the issue is that since offline messages are deserialized via jackson, using a byte array would require the
        //relay or web server to store them as base64; need to come back and fix this stuff
        val pkg = Package(PackageId(event.from, randomUUID()), timestamp, event.content)
        val packages = listOf(pkg)

        processPackages(packages) successUi {
            relayClientManager.sendMessageReceivedAck(event.messageId)
        }
    }


    private fun onContactEvent(event: ContactEvent) {
    }

    /** Writes the received message and then fires the new messages subject. */
    private fun writeReceivedSelfMessage(from: UserId, decryptedMessage: String): Promise<Unit, Exception> {
        val messageInfo = MessageInfo.newReceived(decryptedMessage, currentTimestamp(), 0)
        return messagePersistenceManager.addMessage(from, messageInfo) mapUi { messageInfo ->
            //FIXME
            //newMessagesSubject.onNext(MessageBundle(from, listOf(messageInfo)))
        }
    }

    private fun usersFromPackages(packages: List<Package>): Set<UserId> = packages.mapTo(HashSet()) { it.userId }

    /** Filter out packages which belong to blocked users, etc. */
    private fun filterBlacklisted(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = usersFromPackages(packages)

        return contactsService.allowMessagesFrom(users) map { allowedUsers ->
            val rejected = HashSet(users)
            rejected.removeAll(allowedUsers)
            if (rejected.isNotEmpty())
                log.info("Reject messages from users: {}", rejected.map { it.long })

            packages.filter { it.id.address.id in allowedUsers }
        }
    }

    private fun fetchMissingContactInfo(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = HashSet<UserId>()
        users.addAll(packages.map { it.id.address.id })

        //TODO should probably blacklist invalid ids for a while; prevent using clients to ddos servers by
        //sending them a bunch of invalid ids constantly
        return contactsService.addMissingContacts(users) map { invalidIds ->
            packages.filter { !invalidIds.contains(it.userId) }
        } fail { e ->
            log.error("Failed to add packages to queue: {}", e.message, e)
        }
    }

    /** Filter out blocked users, fetch any missing contact info. */
    private fun preprocessPackages(packages: List<Package>): Promise<List<Package>, Exception> {
        return filterBlacklisted(packages) bind { filtered ->
            fetchMissingContactInfo(filtered)
        }
    }

    /** Drops blacklisted IDs, fetches missing contact data and passes results to MessageReceiverService. */
    private fun processPackages(packages: List<Package>): Promise<Unit, Exception> {
        //since when we receive stuff we're always online, it makes sense that we should handle the contact info fetch as well as part of this process
        //by doing so, we keep the actual decryption free of network requirements
        //XXX although we need it to add new group members anyways... just keep this here for now
        return preprocessPackages(packages) bind { filtered ->
            messageReceiver.processPackages(filtered)
        }
    }

    /* UIMessengerService interface */

    override fun sendMessageTo(userId: UserId, message: String): Promise<MessageInfo, Exception> {
        val isSelfMessage = userId == selfId

        //HACK
        //trying to send to yourself tries to use the same session for both ends, which ends up failing with a bad mac exception
        return if (!isSelfMessage) {
            val messageInfo = MessageInfo.newSent(message, 0)
            val m = TextMessage(messageInfo.timestamp, message, null)
            val wrapper = TextMessageWrapper(m)

            val serialized = objectMapper.writeValueAsBytes(wrapper)

            val metadata = MessageMetadata(userId, null, MessageCategory.TEXT_SINGLE, messageInfo.id)
            messageSender.addToQueue(metadata, serialized) bind {
                messagePersistenceManager.addMessage(userId, messageInfo)
            }
        }
        else {
            val messageInfo = MessageInfo.newSelfSent(message, 0)
            //we need to insure that the send message info is sent back to the ui before the ServerReceivedMessage is fired
            messagePersistenceManager.addMessage(userId, messageInfo) map { messageInfo ->
                Thread.sleep(30)
                messageInfo
            } successUi { messageInfo ->
                writeReceivedSelfMessage(userId, messageInfo.message)
            }
        }
    }

    override fun sendGroupMessageTo(groupId: GroupId, message: String): Promise<MessageInfo, Exception> {
        return groupPersistenceManager.getGroupMembers(groupId) bindUi { members ->
            val p = if (members.isNotEmpty()) {
                val m = TextMessage(currentTimestamp(), message, groupId)

                val serialized = objectMapper.writeValueAsBytes(TextMessageWrapper(m))

                val messages = members.map {
                    val metadata = MessageMetadata(it, groupId, MessageCategory.TEXT_GROUP, randomUUID())
                    MessageEntry(metadata, serialized)
                }
                messageSender.addToQueue(messages)
            }
            else
                Promise.ofSuccess(Unit)

            p bind {
                val messageInfo = MessageInfo.newSent(message, 0)
                groupPersistenceManager.addMessage(groupId, null, messageInfo)
            }
        }
    }

    override fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(userId, startingAt, count)
    }

    override fun getConversations(): Promise<List<Conversation>, Exception> {
        return contactsPersistenceManager.getAllConversations()
    }

    override fun markConversationAsRead(userId: UserId): Promise<Unit, Exception> {
        return contactsPersistenceManager.markConversationAsRead(userId)
    }

    override fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteMessages(userId, messageIds)
    }

    override fun deleteAllMessages(userId: UserId): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteAllMessages(userId)
    }

    /* Other */

    override fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception> {
        return processPackages(offlineMessages)
    }
}
