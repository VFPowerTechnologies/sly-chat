package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.persistence.ConversationId

data class RecentChatInfo(
        val id: ConversationId,
        var groupName: String?,
        var lastSpeakerName: String,
        var lastTimestamp: Long,
        var lastMessage: String?,
        var unreadMessageCount: Int
)