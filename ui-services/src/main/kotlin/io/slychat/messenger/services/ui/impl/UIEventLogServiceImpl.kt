package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.services.EventLogService
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIEventLogService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Observable
import rx.subscriptions.CompositeSubscription

data class UILogEvent(
    val timestamp: Long,
    val info: String
)

class UIEventLogServiceImpl(
    userSessionAvailable: Observable<UserComponent?>
) : UIEventLogService {
    private val subscriptions = CompositeSubscription()

    private var eventLogService: EventLogService? = null

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent == null) {
            eventLogService = null
            subscriptions.clear()
        }
        else {
            eventLogService = userComponent.eventLogService
        }
    }

    private fun getEventLogServiceOrThrow(): EventLogService {
        return eventLogService ?: error("Not logged in")
    }

    override fun getSecurityEvents(startingAt: Int, count: Int): Promise<List<UILogEvent>, Exception> {
        return getEventLogServiceOrThrow().getEvents(setOf(LogEventType.SECURITY), null, startingAt, count) map {
            it.map {
                val info = it.data.toDisplayString()

                UILogEvent(it.timestamp, info)
            }
        }
    }
}