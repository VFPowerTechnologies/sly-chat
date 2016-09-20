package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

data class ConversationDisplayInfo(
    val conversationId: ConversationId,
    val groupName: String?,
    //the actual full unread count; may be larger than latestUnreadMessageIds
    val unreadCount: Int,
    //this will actually contained a limited amount of ids
    //this is for perf/mem usage reasons, since we don't need the full list for our use case
    //this is simply for the notification system
    val latestUnreadMessageIds: List<String>,
    //may be null if no one has spoke yet
    val lastMessageData: LastMessageData?
) {
    init {
        require(unreadCount >= 0) { "unreadCount must be >= 0, got $unreadCount" }
        
        if (lastMessageData == null && unreadCount != 0)
            throw IllegalArgumentException("unreadCount must be 0 if lastMessageData is null")
    }
}

data class LastMessageData(
    val speakerName: String?,
    val speakerId: UserId?,
    val message: String,
    val timestamp: Long
)
