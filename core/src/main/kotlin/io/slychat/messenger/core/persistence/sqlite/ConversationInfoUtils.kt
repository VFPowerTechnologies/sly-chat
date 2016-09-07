package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*

class ConversationInfoUtils {
    private fun rowToConversationInfo(stmt: SQLiteStatement): ConversationInfo {
        return ConversationInfo(
            stmt.columnNullableLong(0)?.let { UserId(it) },
            stmt.columnInt(1),
            stmt.columnString(2),
            stmt.columnNullableLong(3)
        )
    }

    fun getConversationInfo(connection: SQLiteConnection, conversationId: ConversationId): ConversationInfo? {
        return connection.withPrepared("SELECT last_speaker_contact_id, unread_count, last_message, last_timestamp FROM conversation_info WHERE conversation_id=?") { stmt ->
            stmt.bind(1, conversationId)

            if (stmt.step())
                rowToConversationInfo(stmt)
            else
                null
        }
    }

    fun getAllUserConversations(connection: SQLiteConnection): List<UserConversation> {
        val sql = """
SELECT
    id,
    email,
    name,
    allowed_message_level,
    phone_number,
    public_key,
    unread_count,
    last_message,
    last_timestamp
FROM
    contacts
JOIN
    conversation_info
ON
    contacts.id=substr(conversation_info.conversation_id, 2)
WHERE
    conversation_info.conversation_id LIKE 'U%'
        """

        return connection.withPrepared(sql) { stmt ->
            stmt.map { stmt ->
                val contact = contactInfoFromRow(stmt)
                val lastTimestamp = stmt.columnNullableLong(8)
                val info = ConversationInfo(contact.id, stmt.columnInt(6), stmt.columnString(7), lastTimestamp)
                UserConversation(contact, info)
            }
        }
    }

    fun getAllGroupConversations(connection: SQLiteConnection): List<GroupConversation> {
        val sql =
            """
SELECT
    c.last_speaker_contact_id,
    c.unread_count,
    c.last_message,
    c.last_timestamp,
    g.id,
    g.name,
    g.membership_level
FROM
    conversation_info
AS
    c
JOIN
    groups
AS
    g
ON
    g.id=substr(c.conversation_id, 2)
WHERE
    c.conversation_id LIKE 'G%'
AND
    g.membership_level=?
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, GroupMembershipLevel.JOINED)
            stmt.map {
                val groupInfo = rowToGroupInfo(stmt, 4)
                val convoInfo = rowToConversationInfo(it)

                GroupConversation(groupInfo, convoInfo)
            }
        }
    }
}