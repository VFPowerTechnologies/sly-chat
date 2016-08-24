package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.MessageInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(Recipient.User::class, name = "u"),
    JsonSubTypes.Type(Recipient.Group::class, name = "g")
)
sealed class Recipient {
    class User(@JsonProperty("id") val id: UserId) : Recipient() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as User

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    class Group(@JsonProperty("id") val id: GroupId) : Recipient() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Group

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
}

data class SyncSentMessageInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("recipient")
    val recipient: Recipient,
    @JsonProperty("message")
    val message: String,
    @JsonProperty("timestamp")
    val timestamp: Long,
    @JsonProperty("receivedTimestamp")
    val receivedTimestamp: Long
) {
    fun toMessageInfo(): MessageInfo {
        return MessageInfo(
            id,
            message,
            timestamp,
            receivedTimestamp,
            true,
            true,
            0
        )
    }
}