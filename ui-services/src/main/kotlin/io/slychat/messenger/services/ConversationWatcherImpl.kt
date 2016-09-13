package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.toConversationId
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageService
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject

class ConversationWatcherImpl(
    uiEvents: Observable<UIEvent>,
    private val messageService: MessageService
) : ConversationWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private val messageUpdatesSubject = PublishSubject.create<ConversationMessage>()

    private var subscription: Subscription? = null

    override val messageUpdates: Observable<ConversationMessage>
        get() = messageUpdatesSubject

    init {
        subscription = uiEvents.subscribe { onUiEvent(it) }
    }

    override fun init() {}

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun onUiEvent(event: UIEvent) {
        when (event) {
            is UIEvent.PageChange -> {
                val conversationId = when (event.page) {
                    PageType.CONVO ->
                        UserId(event.extra.toLong()).toConversationId()

                    PageType.GROUP ->
                        GroupId(event.extra).toConversationId()

                    else -> null
                }

                if (conversationId != null) {
                    messageService.markConversationAsRead(conversationId) fail {
                        log.error("Failed to mark conversation {} as read: {}", conversationId, it.message, it)
                    }
                }
            }
        }
    }
}