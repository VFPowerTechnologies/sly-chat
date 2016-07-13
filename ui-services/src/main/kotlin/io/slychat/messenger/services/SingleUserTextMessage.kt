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
interface GroupEvent

data class GroupJoin(
    val id: GroupId,
    val joined: UserId
) : GroupEvent

data class GroupPart(
    val id: GroupId,
    val parted: UserId
) : GroupEvent

data class GroupInvitation(
    val id: GroupId,
    val name: String,
    val members: Set<UserId>
) : GroupEvent
