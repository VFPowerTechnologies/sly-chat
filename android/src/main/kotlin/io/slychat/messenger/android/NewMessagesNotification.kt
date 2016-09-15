package io.slychat.messenger.android

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.core.persistence.ConversationId
import java.util.*

/** The latest available message for a conversation. */
data class NewMessageData(
    val groupName: String?,
    val speakerName: String,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)

/** Current state of the new messages notification. */
class NewMessagesNotification {
    val contents = HashMap<ConversationId, NewMessageData>()

    fun hasNewMessages(): Boolean = contents.isNotEmpty()
    fun userCount(): Int = contents.size

    fun clear() {
        contents.clear()
    }

    /** Increases the unread count by the amount given in newMessageData. */
    fun update(conversationDisplayInfo: ConversationDisplayInfo) {
        if (conversationDisplayInfo.unreadCount == 0)
            contents.remove(conversationDisplayInfo.conversationId)
        else {
            //constructor prevents lastMessageData being null if unreadCount is 0
            val lastMessageData = conversationDisplayInfo.lastMessageData!!
            contents[conversationDisplayInfo.conversationId] = NewMessageData(
                conversationDisplayInfo.groupName,
                lastMessageData.speakerName,
                lastMessageData.message,
                lastMessageData.timestamp,
                conversationDisplayInfo.unreadCount
            )
        }
    }
}