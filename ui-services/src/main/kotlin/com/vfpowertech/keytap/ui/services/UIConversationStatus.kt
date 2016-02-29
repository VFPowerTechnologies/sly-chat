package com.vfpowertech.keytap.ui.services

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Information about a conversation with a contact.
 *
 * @property isOnline Whether or not the user is currently online.
 * @property unreadMessageCount Number of unread messages for this conversation.
 * @property lastMessage Last received Message
 */
data class UIConversationStatus(
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("unreadMessageCount") val unreadMessageCount: Int,
    @JsonProperty("lastMessage") val lastMessage: String?
)