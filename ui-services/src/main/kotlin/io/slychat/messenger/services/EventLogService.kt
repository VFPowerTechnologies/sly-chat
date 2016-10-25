package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.core.persistence.LogTarget
import nl.komponents.kovenant.Promise
import rx.Observable

/** Implementations must be thread-safe; eventLogEvents must only emit on the main thread. */
interface EventLogService {
    val eventLogEvents: Observable<EventLogEvent>

    fun addEvent(event: LogEvent): Promise<Unit, Exception>

    fun getEvents(types: Set<LogEventType>, target: LogTarget?, startingAt: Int, count: Int): Promise<List<LogEvent>, Exception>

    fun deleteEventRange(types: Set<LogEventType>, target: LogTarget?, startTimestamp: Long, endTimestamp: Long): Promise<Unit, Exception>

    fun deleteEvents(types: Set<LogEventType>, target: LogTarget?): Promise<Unit, Exception>
}