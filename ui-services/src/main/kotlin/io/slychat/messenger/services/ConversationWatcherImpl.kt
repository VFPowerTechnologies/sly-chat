package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.messaging.ConversationMessage
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject

class ConversationWatcherImpl(
    uiEvents: Observable<UIEvent>,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupService: GroupService
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
                when (event.page) {
                    PageType.CONVO -> {
                        val userId = UserId(event.extra.toLong())

                        contactsPersistenceManager.markConversationAsRead(userId) fail {
                            log.error("Failed to mark conversation for {} as read: {}", userId, it.message, it)
                        }
                    }

                    PageType.GROUP -> {
                        val groupId = GroupId(event.extra)

                        groupService.markConversationAsRead(groupId) fail {
                            log.error("Failed to mark conversation for {} as read: {}", groupId, it.message, it)
                        }
                    }
                }
            }
        }
    }
}