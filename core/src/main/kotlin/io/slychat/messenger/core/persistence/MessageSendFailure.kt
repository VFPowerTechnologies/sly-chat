package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(MessageSendFailure.InactiveUser::class, name = "inactiveUser"),
    JsonSubTypes.Type(MessageSendFailure.EncryptionFailure::class, name = "encryptionFailure")
)
sealed class MessageSendFailure {
    @get:JsonIgnore
    abstract val isRetryable: Boolean

    class InactiveUser : MessageSendFailure() {
        override val isRetryable: Boolean
            get() = false

        override fun equals(other: Any?): Boolean {
            return other is InactiveUser
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            return "InactiveUser()"
        }
    }

    class EncryptionFailure : MessageSendFailure() {
        override val isRetryable: Boolean
            get() = false

        override fun equals(other: Any?): Boolean {
            return other is EncryptionFailure
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            return "EncryptionFailure()"
        }
    }
}