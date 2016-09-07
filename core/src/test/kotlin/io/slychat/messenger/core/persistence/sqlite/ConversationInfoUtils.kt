package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationInfo
import io.slychat.messenger.core.persistence.GroupId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationInfoUtils(
    private val persistenceManager: SQLitePersistenceManager
) {
    fun doesConvTableExist(conversationId: ConversationId): Boolean =
        persistenceManager.syncRunQuery { ConversationTable.exists(it, conversationId) }

    fun assertConvTableExists(userId: UserId, message: String? = null) = assertConvTableExists(ConversationId(userId), message)
    fun assertConvTableNotExists(userId: UserId, message: String? = null) = assertConvTableNotExists(ConversationId(userId), message)

    fun assertConvTableExists(groupId: GroupId, message: String? = null) = assertConvTableExists(ConversationId(groupId), message)
    fun assertConvTableNotExists(groupId: GroupId, message: String? = null) = assertConvTableNotExists(ConversationId(groupId), message)

    fun assertConvTableExists(conversationId: ConversationId, message: String?) {
        val m = message ?: "Conversation table for $conversationId doesn't exists"
        assertTrue(doesConvTableExist(conversationId), m)
    }

    fun assertConvTableNotExists(conversationId: ConversationId, message: String?) {
        val m = message ?: "Conversation table for $conversationId exists"
        assertFalse(doesConvTableExist(conversationId), m)
    }

    fun getConversationInfo(userId: UserId): ConversationInfo = getConversationInfo(ConversationId(userId))
    fun getConversationInfo(groupId: GroupId): ConversationInfo = getConversationInfo(ConversationId(groupId))

    fun getConversationInfo(conversationId: ConversationId): ConversationInfo {
        TODO()
    }

    fun setConversationInfo(conversationId: ConversationId, conversationInfo: ConversationInfo) = persistenceManager.syncRunQuery {
        it.withPrepared("UPDATE conversation_info SET last_speaker_contact_id=?, last_message=?, last_timestamp=?, unread_count=? WHERE conversation_id=?") { stmt ->
            stmt.bind(1, conversationInfo.lastSpeaker)
            stmt.bind(2, conversationInfo.lastMessage)
            stmt.bind(3, conversationInfo.lastTimestamp)
            stmt.bind(4, conversationInfo.unreadMessageCount)
            stmt.bind(5, conversationId)
            stmt.step()
        }
    }
}