package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.toConversationId
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageService
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription

class ConversationWatcherImpl(
    uiEvents: Observable<UIEvent>,
    uiVisibility: Observable<Boolean>,
    private val messageService: MessageService
) : ConversationWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private val messageUpdatesSubject = PublishSubject.create<ConversationMessage>()

    private var subscriptions = CompositeSubscription()

    private var currentConversationId: ConversationId? = null

    override val messageUpdates: Observable<ConversationMessage>
        get() = messageUpdatesSubject

    init {
        subscriptions.add(uiEvents.subscribe { onUiEvent(it) })
        subscriptions.add(uiVisibility.subscribe { onUiVisibilityChanged(it) })
    }

    private fun onUiVisibilityChanged(isVisible: Boolean) {
        val conversationId = currentConversationId
        if (isVisible && conversationId != null) {
            markConversationAsRead(conversationId)
        }
    }

    override fun init() {}

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun markConversationAsRead(conversationId: ConversationId) {
        messageService.markConversationAsRead(conversationId) fail {
            log.error("Failed to mark conversation {} as read: {}", conversationId, it.message, it)
        }
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

                if (conversationId != null)
                    markConversationAsRead(conversationId)

                currentConversationId = conversationId
            }
        }
    }
}