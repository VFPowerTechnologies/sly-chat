package io.slychat.messenger.core.persistence

data class ConversationDisplayInfo(
    val conversationId: ConversationId,
    val groupName: String?,
    val unreadCount: Int,
    //may be null if no one has spoke yet
    val lastMessageData: LastMessageData?
) {
    init {
        require(unreadCount >= 0) { "unreadCount must be >= 0, got $unreadCount" }
    }
}

data class LastMessageData(
    val speakerName: String,
    val message: String,
    val timestamp: Long
)
