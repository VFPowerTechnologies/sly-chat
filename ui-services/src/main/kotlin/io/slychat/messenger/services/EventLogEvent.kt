package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.core.persistence.LogTarget

sealed class EventLogEvent {
    class Added(val event: LogEvent) : EventLogEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Added

            if (event != other.event) return false

            return true
        }

        override fun hashCode(): Int {
            return event.hashCode()
        }

        override fun toString(): String {
            return "Added(event=$event)"
        }
    }

    //timestamps are -1 when unused
    class Deleted(val types: Set<LogEventType>, val target: LogTarget?, val startTimestamp: Long, val endTimestamp: Long) : EventLogEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Deleted

            if (types != other.types) return false
            if (target != other.target) return false
            if (startTimestamp != other.startTimestamp) return false
            if (endTimestamp != other.endTimestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = types.hashCode()
            result = 31 * result + (target?.hashCode() ?: 0)
            result = 31 * result + startTimestamp.hashCode()
            result = 31 * result + endTimestamp.hashCode()
            return result
        }

        override fun toString(): String {
            return "Deleted(types=$types, target=$target, startTimestamp=$startTimestamp, endTimestamp=$endTimestamp)"
        }
    }
}