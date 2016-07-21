package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.readResourceFileText

/**
 * Utility object for creating and deleting group conversation tables.
 *
 * This should will do (minor) disk io on initialization for caching the table template.
 *
 * Since this is accessed only within the db thread pool, this isn't an issue.
 */
object GroupConversationTable {
    private val tableTemplate: String
    init {
        tableTemplate = javaClass.readResourceFileText("/schema/group_conversation.sql")
    }

    fun getTablenameForContact(groupId: GroupId) =
        "group_conv_${groupId.string}"

    fun create(connection: SQLiteConnection, groupId: GroupId) {
        val sql = tableTemplate.replace("%id%", groupId.string)
        connection.exec(sql)
    }

    fun delete(connection: SQLiteConnection, groupId: GroupId) {
        connection.exec("DROP TABLE IF EXISTS `group_conv_${groupId.string}`")
    }

    fun exists(connection: SQLiteConnection, groupId: GroupId): Boolean {
        connection.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { stmt ->
            stmt.bind(1, getTablenameForContact(groupId))
            return stmt.step()
        }
    }

    fun getGroupConversationTableNames(connection: SQLiteConnection): List<String> {
        return connection.prepare("SELECT id FROM groups").use { stmt ->
            stmt.map { it.columnString(0) }
                .map { getTablenameForContact(GroupId(it)) }
        }
    }
}