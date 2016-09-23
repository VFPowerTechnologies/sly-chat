package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.MessageId

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(SlyMessage.GroupEvent::class, name = "g"),
    JsonSubTypes.Type(SlyMessage.Text::class, name = "t"),
    JsonSubTypes.Type(SlyMessage.Sync::class, name = "s"),
    JsonSubTypes.Type(SlyMessage.Control::class, name = "c")
)
sealed class SlyMessage {
    class GroupEvent(@JsonProperty("m") val m: GroupEventMessage) : SlyMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as GroupEvent

            if (m != other.m) return false

            return true
        }

        override fun hashCode(): Int {
            return m.hashCode()
        }

        override fun toString(): String {
            return "GroupEvent(m=$m)"
        }
    }

    class Text(@JsonProperty("m") val m: TextMessage) : SlyMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Text

            if (m != other.m) return false

            return true
        }

        override fun hashCode(): Int {
            return m.hashCode()
        }

        override fun toString(): String {
            return "Text(m=$m)"
        }
    }

    class Sync(@JsonProperty("m") val m: SyncMessage) : SlyMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Sync

            if (m != other.m) return false

            return true
        }

        override fun hashCode(): Int {
            return m.hashCode()
        }

        override fun toString(): String {
            return "Sync(m=$m)"
        }
    }

    class Control(@JsonProperty("m") val m: ControlMessage) : SlyMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Control

            if (m != other.m) return false

            return true
        }

        override fun hashCode(): Int {
            return m.hashCode()
        }

        override fun toString(): String {
            return "Control(m=$m)"
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TextMessage(
    @JsonProperty("id")
    val id: MessageId,
    @JsonProperty("timestamp")
    val timestamp: Long,
    @JsonProperty("message")
    val message: String,
    @JsonProperty("groupId")
    val groupId: GroupId?,
    @JsonProperty("ttl")
    val ttlMs: Long
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(GroupEventMessage.Join::class, name = "j"),
    JsonSubTypes.Type(GroupEventMessage.Part::class, name = "p"),
    JsonSubTypes.Type(GroupEventMessage.Invitation::class, name = "i")
)
sealed class GroupEventMessage {
    abstract val id: GroupId

    /** A user has joined the group. Sent from the user sending the GroupInvitation. Must be sent from a current member of the group. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    class Join(
        @JsonProperty("id")
        override val id: GroupId,
        @JsonProperty("joined")
        val joined: Set<UserId>
    ) : GroupEventMessage() {
        constructor(id: GroupId, joined: UserId) : this(id, setOf(joined))

        override fun toString(): String {
            return "Join(id=$id, joined=$joined)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Join

            if (id != other.id) return false
            if (joined != other.joined) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + joined.hashCode()
            return result
        }
    }

    /** Sender has left the group. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    class Part(
        @JsonProperty("id")
        override val id: GroupId
    ) : GroupEventMessage() {
        override fun toString(): String {
            return "Part(id=$id)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Part

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    /** Invitation to a new group. The sender is implicitly considered as a member and thus is not included in the member list. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    class Invitation(
        @JsonProperty("id")
        override val id: GroupId,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("members")
        val members: Set<UserId>
    ) : GroupEventMessage() {
        override fun toString(): String {
            return "Invitation(id=$id, name='$name', members=$members)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Invitation

            if (id != other.id) return false
            if (name != other.name) return false
            if (members != other.members) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + members.hashCode()
            return result
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(SyncMessage.NewDevice::class, name = "d"),
    JsonSubTypes.Type(SyncMessage.SelfMessage::class, name = "m"),
    JsonSubTypes.Type(SyncMessage.AddressBookSync::class, name = "s"),
    JsonSubTypes.Type(SyncMessage.MessageExpired::class, name = "e"),
    JsonSubTypes.Type(SyncMessage.MessagesRead::class, name = "r"),
    JsonSubTypes.Type(SyncMessage.MessagesDeleted::class, name = "md"),
    JsonSubTypes.Type(SyncMessage.MessagesDeletedAll::class, name = "mda")
)
sealed class SyncMessage {
    @JsonIgnoreProperties(ignoreUnknown = true)
    class NewDevice(
        @JsonProperty("deviceInfo")
        val deviceInfo: DeviceInfo
    ) : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as NewDevice

            if (deviceInfo != other.deviceInfo) return false

            return true
        }

        override fun hashCode(): Int {
            return deviceInfo.hashCode()
        }

        override fun toString(): String {
            return "NewDevice(deviceInfo=$deviceInfo)"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class MessageExpired(
        @JsonProperty("conversationId")
        val conversationId: ConversationId,
        @JsonProperty("messageId")
        val messageId: MessageId
    ) : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MessageExpired

            if (conversationId != other.conversationId) return false
            if (messageId != other.messageId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageId.hashCode()
            return result
        }

        override fun toString(): String {
            return "MessageExpired(conversationId=$conversationId, messageId=$messageId)"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class SelfMessage(
        @JsonProperty("sentMessageInfo")
        val sentMessageInfo: SyncSentMessageInfo
    ) : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as SelfMessage

            if (sentMessageInfo != other.sentMessageInfo) return false

            return true
        }

        override fun hashCode(): Int {
            return sentMessageInfo.hashCode()
        }

        override fun toString(): String {
            return "SelfMessage(sentMessageInfo=$sentMessageInfo)"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class AddressBookSync() : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            return other?.javaClass == javaClass
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            return "AddressBookSync()"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class MessagesRead(
        @JsonProperty("conversationId")
        val conversationId: ConversationId,
        @JsonProperty("messageIds")
        val messageIds: List<MessageId>
    ) : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MessagesRead

            if (conversationId != other.conversationId) return false
            if (messageIds != other.messageIds) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageIds.hashCode()
            return result
        }

        override fun toString(): String {
            return "MessagesRead(conversationId=$conversationId, messageIds=$messageIds)"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class MessagesDeleted(
        @JsonProperty("conversationId")
        val conversationId: ConversationId,
        @JsonProperty("messageIds")
        val messageIds: List<MessageId>
    ) : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MessagesDeleted

            if (conversationId != other.conversationId) return false
            if (messageIds != other.messageIds) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageIds.hashCode()
            return result
        }

        override fun toString(): String {
            return "MessagesDeleted(conversationId=$conversationId, messageIds=$messageIds)"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class MessagesDeletedAll(
        @JsonProperty("conversationId")
        val conversationId: ConversationId,
        @JsonProperty("lastMessageTimestamp")
        val lastMessageTimestamp: Long
    ) : SyncMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MessagesDeletedAll

            if (conversationId != other.conversationId) return false
            if (lastMessageTimestamp != other.lastMessageTimestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + lastMessageTimestamp.hashCode()
            return result
        }

        override fun toString(): String {
            return "MessagesDeletedAll(conversationId=$conversationId, lastMessageTimestamp=$lastMessageTimestamp)"
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(ControlMessage.WasAdded::class, name = "a")
)
sealed class ControlMessage {
    /** Sender added you as a contact. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    class WasAdded() : ControlMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            return other?.javaClass == javaClass
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            return "WasAdded()"
        }
    }
}
