package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

sealed class LogEvent {
    abstract val target: LogTarget
    abstract val timestamp: Long
    abstract val type: LogEventType
    abstract val data: EventData

    class Security(
        @JsonProperty("target")
        override val target: LogTarget,
        @JsonProperty("timestamp")
        override val timestamp: Long,
        @JsonProperty("data")
        override val data: SecurityEventData
    ) : LogEvent() {
        @get:JsonIgnore
        override val type: LogEventType
            get() = LogEventType.SECURITY

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Security

            if (target != other.target) return false
            if (timestamp != other.timestamp) return false
            if (data != other.data) return false

            return true
        }

        override fun hashCode(): Int {
            var result = target.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + data.hashCode()
            return result
        }

        override fun toString(): String {
            return "Security(target=$target, timestamp=$timestamp, data=$data)"
        }
    }
}