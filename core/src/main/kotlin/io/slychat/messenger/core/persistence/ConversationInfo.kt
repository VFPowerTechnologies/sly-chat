package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/**
 * Information about a conversation.
 *
 * @param lastMessage Last message in the conversation.
 */
data class ConversationInfo(
    val lastSpeaker: UserId?,
    val unreadMessageCount: Int,
    val lastMessage: String?,
    val lastTimestamp: Long?
) {
    init {
        require(unreadMessageCount >= 0) { "unreadMessageCount must be >= 0" }
    }
}