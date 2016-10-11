package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Information about a conversation with a contact.
 *
 * @property isOnline Whether or not the user is currently online.
 * @property unreadMessageCount Number of unread messages for this conversation.
 * @property lastMessage Last received Message
 */
data class UIConversationInfo(
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("unreadMessageCount") val unreadMessageCount: Int,
    @JsonProperty("lastMessage") val lastMessage: String?,
    @JsonProperty("lastTimestamp") val lastTimestamp: Long?
)