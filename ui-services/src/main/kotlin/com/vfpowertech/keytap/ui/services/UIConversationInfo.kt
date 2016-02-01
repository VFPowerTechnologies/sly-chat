package com.vfpowertech.keytap.ui.services

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Information about a conversation with a contact.
 *
 * @property unreadMessageCount Number of unread messages for this conversation.
 * @property lastMessage Last received Message
 */
data class UIConversationInfo(
    @JsonProperty("unreadMessageCount") val unreadMessageCount: Int,
    @JsonProperty("lastMessage") val lastMessage: UIMessage?
)