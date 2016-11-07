package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface EventLog {
    fun addEvent(event: LogEvent): Promise<Unit, Exception>

    /**
     * Retrieves the last count items at the given offset matching the types and target.
     *
     * @type Empty set to match all types.
     * @target Null to match all targets
     */
    fun getEvents(types: Set<LogEventType>, target: LogTarget?, startingAt: Int, count: Int): Promise<List<LogEvent>, Exception>

    /** Deletes all matching entries within the [startTimestamp, endTimestamp] range. */
    fun deleteEventRange(types: Set<LogEventType>, target: LogTarget?, startTimestamp: Long, endTimestamp: Long): Promise<Unit, Exception>

    /** Removes all matching entries. */
    fun deleteEvents(types: Set<LogEventType>, target: LogTarget?): Promise<Unit, Exception>
}
