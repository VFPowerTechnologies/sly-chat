package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.EventLog
import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.core.persistence.LogTarget
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class EventLogServiceImpl(
    private val eventLog: EventLog
) : EventLogService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val eventLogEventsSubject = BehaviorSubject.create<EventLogEvent>()

    override val eventLogEvents: Observable<EventLogEvent>
        get() = eventLogEventsSubject

    override fun addEvent(event: LogEvent): Promise<Unit, Exception> {
        return eventLog.addEvent(event) successUi {
            eventLogEventsSubject.onNext(EventLogEvent.Added(event))
        } fail {
            log.error("Failed to add event: {}", it.message, it)
        }
    }

    override fun getEvents(types: Set<LogEventType>, target: LogTarget?, startingAt: Int, count: Int): Promise<List<LogEvent>, Exception> {
        return eventLog.getEvents(types, target, startingAt, count)
    }

    override fun deleteEventRange(types: Set<LogEventType>, target: LogTarget?, startTimestamp: Long, endTimestamp: Long): Promise<Unit, Exception> {
        return eventLog.deleteEventRange(types, target, startTimestamp, endTimestamp) successUi {
            eventLogEventsSubject.onNext(EventLogEvent.Deleted(types, target, startTimestamp, endTimestamp))
        }
    }

    override fun deleteEvents(types: Set<LogEventType>, target: LogTarget?): Promise<Unit, Exception> {
        return eventLog.deleteEvents(types, target) successUi {
            eventLogEventsSubject.onNext(EventLogEvent.Deleted(types, target, -1, -1))
        }
    }
}