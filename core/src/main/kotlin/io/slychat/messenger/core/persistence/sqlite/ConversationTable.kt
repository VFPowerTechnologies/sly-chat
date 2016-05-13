package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.readResourceFileText

/**
 * Utility object for creating and deleting conversation tables.
 *
 * This should will do (minor) disk io on initialization for caching the table template.
 */
object ConversationTable {
    private val tableTemplate: String
    init {
        tableTemplate = javaClass.readResourceFileText("/schema/conversation.sql")
    }

    fun getTablenameForContact(userId: UserId) =
        "conv_${userId.long}"

    fun create(connection: SQLiteConnection, userId: UserId) {
        val sql = tableTemplate.replace("%id%", userId.long.toString())
        connection.exec(sql)
    }

    fun delete(connection: SQLiteConnection, userId: UserId) {
        connection.exec("DROP TABLE IF EXISTS `conv_${userId.long}`")
    }

    fun exists(connection: SQLiteConnection, userId: UserId): Boolean {
        connection.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { stmt ->
            stmt.bind(1, getTablenameForContact(userId))
            return stmt.step()
        }
    }
}