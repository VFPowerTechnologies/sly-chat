package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.MessageMetadata
import io.slychat.messenger.core.persistence.MessageSendFailure

sealed class MessageSendRecord {
    abstract val metadata: MessageMetadata

    class Ok(override val metadata: MessageMetadata, val serverReceivedTimestamp: Long) : MessageSendRecord() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Ok

            if (metadata != other.metadata) return false
            if (serverReceivedTimestamp != other.serverReceivedTimestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = metadata.hashCode()
            result = 31 * result + serverReceivedTimestamp.hashCode()
            return result
        }

        override fun toString(): String {
            return "Ok(metadata=$metadata, serverReceivedTimestamp=$serverReceivedTimestamp)"
        }
    }

    class Failure(override val metadata: MessageMetadata, val failure: MessageSendFailure) : MessageSendRecord() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Failure

            if (metadata != other.metadata) return false
            if (failure != other.failure) return false

            return true
        }

        override fun hashCode(): Int {
            var result = metadata.hashCode()
            result = 31 * result + failure.hashCode()
            return result
        }

        override fun toString(): String {
            return "Failure(metadata=$metadata, failure=$failure)"
        }
    }
}