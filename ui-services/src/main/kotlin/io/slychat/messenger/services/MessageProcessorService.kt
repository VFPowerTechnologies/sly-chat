package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

/** Handles incoming messages. */
interface MessageProcessorService {
    val newMessages: Observable<MessageBundle>

    fun processMessages(messages: List<SlyMessageWrapper>)

    //TODO self-messages
    //maybe send a self-message via processMessages
    //for the ui side, just return the generated MessageInfo and do the db write in the bg
    //if this fails, then ???; right now we just essentially crash anyways
}

data class SlyMessageWrapper(
    val sender: UserId,
    val messageId: String,
    val message: SlyMessage
)

class MessageProcessorServiceImpl(
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager
) : MessageProcessorService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    override val newMessages: Observable<MessageBundle> = newMessagesSubject

    //XXX needs to return a Promise so we can move on to the next message

    //needs to return a Promise so we know when to continue processing; can't rely on events since there's so many types
    //XXX also we might have to do diff types of message storage (group updates, etc)...
    //need to process group events first, and sync messages as well
    //XXX I think I'm just gonna do the less efficient route of processing each message at once... it'll simplify things
    //since we need to process things like group events, sync self sent (this needs to be processed in the proper order as well), as well as normal text messages
    //this is an issue for text messages though, since bundles work great for not setting off like 30 notifications in a row...
    //maybe just use some rx operator to get around this? nfi
    //or maybe just listen for events and buffer them until this finalizes?
    //we can't move on until we've processed all these messages
    override fun processMessages(messages: List<SlyMessageWrapper>) {
        val textMessageInfo = ArrayList<MessageInfo>()

        messages.forEach {
            val m = it.message

            when (m) {
                is TextMessageWrapper -> {
                    val message = m.m
                    textMessageInfo.add(
                        MessageInfo.newReceived(it.messageId, message.message, message.timestamp, currentTimestamp(), 0)
                    )
                }

                else -> {
                    log.error("Unhandled message type: {}", m.javaClass.name)
                }
            }
        }

        if (textMessageInfo.isNotEmpty()) {
            /*
            messagePersistenceManager.addMessages(from, textMessageInfo) mapUi { messageInfo ->
                val bundle = MessageBundle(from, messageInfo)
                newMessagesSubject.onNext(bundle)

                nextReceiveMessage()
            } fail { e ->
                log.error("Unable to store decrypted messages: {}", e.message, e)
            }
            */
        }
    }
}