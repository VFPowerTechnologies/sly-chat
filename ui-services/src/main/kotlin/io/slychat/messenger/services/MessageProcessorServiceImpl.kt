package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import nl.komponents.kovenant.Promise
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class MessageProcessorServiceImpl(
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager
) : MessageProcessorService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    override val newMessages: Observable<MessageBundle> = newMessagesSubject

    //XXX I think I'm just gonna do the less efficient route of processing each message at once... it'll simplify things
    //since we need to process things like group events, sync self sent (this needs to be processed in the proper order as well), as well as normal text messages
    //this is an issue for text messages though, since bundles work great for not setting off like 30 notifications in a row...
    //maybe just use some rx operator to get around this? nfi
    //or maybe just listen for events and buffer them until this finalizes?
    //we can't move on until we've processed all these messages
    override fun processMessage(userId: UserId, wrapper: SlyMessageWrapper): Promise<Unit, Exception> {
        val textMessageInfo = ArrayList<MessageInfo>()

        val m = wrapper.message

        when (m) {
            is TextMessageWrapper -> {
                val message = m.m
                textMessageInfo.add(
                    MessageInfo.newReceived(wrapper.messageId, message.message, message.timestamp, currentTimestamp(), 0)
                )
            }

            else -> {
                log.error("Unhandled message type: {}", m.javaClass.name)
            }
        }

        return if (textMessageInfo.isNotEmpty()) {
            messagePersistenceManager.addMessages(userId, textMessageInfo) mapUi { messageInfo ->
                val bundle = MessageBundle(userId, messageInfo)
                newMessagesSubject.onNext(bundle)
            } fail { e ->
                log.error("Unable to store decrypted messages: {}", e.message, e)
            }
        }
        else
            Promise.ofSuccess(Unit)
    }
}