package io.slychat.messenger.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(GroupEventWrapper::class, name = "g"),
    JsonSubTypes.Type(TextMessageWrapper::class, name = "t")
)
interface SlyMessage

data class GroupEventWrapper(@JsonProperty("m") val m: GroupEvent) : SlyMessage
data class TextMessageWrapper(@JsonProperty("m") val m: TextMessage) : SlyMessage

data class TextMessage(
    @JsonProperty("timestamp")
    val timestamp: Long,
    @JsonProperty("message")
    val message: String,
    @JsonProperty("groupId")
    val groupId: GroupId?
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(GroupJoin::class, name = "j"),
    JsonSubTypes.Type(GroupPart::class, name = "p"),
    JsonSubTypes.Type(GroupInvitation::class, name = "i")
)
interface GroupEvent {
    val id: GroupId
}

/** A user has joined the group. Sent from the user sending the GroupInvitation. Must be sent from a current member of the group. */
data class GroupJoin(
    override val id: GroupId,
    val joined: UserId
) : GroupEvent

/** Sender has left the group. */
data class GroupPart(
    override val id: GroupId
) : GroupEvent

/** Invitation to a new group. */
data class GroupInvitation(
    override val id: GroupId,
    val name: String,
    val members: Set<UserId>
) : GroupEvent
