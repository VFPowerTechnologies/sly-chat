package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.readResourceFileText

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
        "conv_${userId.id}"

    fun create(connection: SQLiteConnection, userId: UserId) {
        val sql = tableTemplate.replace("%id%", userId.id.toString())
        connection.exec(sql)
    }

    fun delete(connection: SQLiteConnection, userId: UserId) {
        connection.exec("DROP TABLE IF EXISTS `conv_${userId.id}`")
    }

    fun exists(connection: SQLiteConnection, userId: UserId): Boolean {
        connection.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { stmt ->
            stmt.bind(1, getTablenameForContact(userId))
            return stmt.step()
        }
    }
}