package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.core.persistence.LogTarget
import nl.komponents.kovenant.Promise
import rx.Observable
import java.util.*

class MockEventLogService : EventLogService {
    override val eventLogEvents: Observable<EventLogEvent>
        get() = throw UnsupportedOperationException()

    private var events = ArrayList<LogEvent>()

    val loggedEvents: List<LogEvent>
        get() = events

    override fun addEvent(event: LogEvent): Promise<Unit, Exception> {
        events.add(event)
        return Promise.ofSuccess(Unit)
    }

    override fun getEvents(types: Set<LogEventType>, target: LogTarget?, startingAt: Int, count: Int): Promise<List<LogEvent>, Exception> {
        throw UnsupportedOperationException()
    }

    override fun deleteEventRange(types: Set<LogEventType>, target: LogTarget?, startTimestamp: Long, endTimestamp: Long): Promise<Unit, Exception> {
        throw UnsupportedOperationException()
    }

    override fun deleteEvents(types: Set<LogEventType>, target: LogTarget?): Promise<Unit, Exception> {
        throw UnsupportedOperationException()
    }
}