package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupMembershipLevel
import io.slychat.messenger.core.readResourceFileText

/**
 * Utility object for creating and deleting contact conversation tables.
 *
 * This should will do (minor) disk io on initialization for caching the table template.
 *
 * Since this is accessed only within the db thread pool, this isn't an issue.
 */
internal object ConversationTable {
    private val tableTemplate: String = javaClass.readResourceFileText("/schema/conversation.sql")

    fun getTablename(conversationId: ConversationId) =
        "conv_${conversationId.asString()}"

    fun create(connection: SQLiteConnection, conversationId: ConversationId) {
        val sql = tableTemplate.replace("%id%", conversationId.asString())
        connection.exec(sql)
    }

    fun delete(connection: SQLiteConnection, conversationId: ConversationId) {
        val tableName = getTablename(conversationId)
        connection.exec("DROP TABLE IF EXISTS `$tableName`")
    }

    fun exists(connection: SQLiteConnection, userId: UserId): Boolean = exists(connection, ConversationId.User(userId))
    fun exists(connection: SQLiteConnection, groupId: GroupId): Boolean = exists(connection, ConversationId.Group(groupId))

    fun exists(connection: SQLiteConnection, conversationId: ConversationId): Boolean {
        connection.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { stmt ->
            stmt.bind(1, getTablename(conversationId))
            return stmt.step()
        }
    }

    fun getConversationTableNames(connection: SQLiteConnection): List<String> {
        val users = connection.withPrepared("SELECT id FROM contacts WHERE allowed_message_level=?") { stmt ->
            stmt.bind(1, AllowedMessageLevel.ALL)

            stmt.map { UserId(it.columnLong(0)) }
                .map { ConversationTable.getTablename(ConversationId.User(it)) }
        }

        val groups = connection.withPrepared("SELECT id FROM groups WHERE membership_level=?") { stmt ->
            stmt.bind(1, GroupMembershipLevel.JOINED)

            stmt.map { GroupId(it.columnString(0)) }
                .map { ConversationTable.getTablename(ConversationId.Group(it)) }
        }

        return users + groups
    }
}