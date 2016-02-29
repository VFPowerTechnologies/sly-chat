package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
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

    fun create(connection: SQLiteConnection, contact: String) {
        val sql = tableTemplate.replace("%name%", escapeBackticks(contact))
        connection.exec(sql)
    }

    fun delete(connection: SQLiteConnection, contact: String) {
        connection.exec("DROP TABLE IF EXISTS `conv_${escapeBackticks(contact)}`")
    }
}