package com.vfpowertech.keytap.core.persistence

/**
 * Information about a conversation with a contact. Each contact has exactly one conversation.
 *
 * @param lastMessage Last message in the conversation.
 */
data class ConversationInfo(
    val contact: String,
    val unreadMessageCount: Int,
    val lastMessage: String?
) {
    init {
        require(unreadMessageCount >= 0) { "unreadMessageCount must be >= 0" }
    }
}