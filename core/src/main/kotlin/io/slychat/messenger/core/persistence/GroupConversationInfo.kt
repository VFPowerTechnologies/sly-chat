package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/** Information about a group conversation. Each group has exactly one conversation. */
data class GroupConversationInfo(
    val groupId: GroupId,
    val lastSpeaker: UserId,
    val unreadMessageCount: Int,
    val lastMessage: String?,
    val lastTimestamp: Long?
) {
    init {
        require(unreadMessageCount >= 0) { "unreadMessageCount must be >= 0" }
    }
}