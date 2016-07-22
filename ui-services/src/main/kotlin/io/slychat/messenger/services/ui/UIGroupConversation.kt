package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId

data class UIGroupConversation(
    val group: UIGroupInfo,
    val info: UIGroupConversationInfo
)

data class UIGroupConversationInfo(
    val lastSpeaker: UserId?,
    val unreadMessageCount: Int,
    val lastMessage: String?,
    val lastTimestamp: Long?
)
