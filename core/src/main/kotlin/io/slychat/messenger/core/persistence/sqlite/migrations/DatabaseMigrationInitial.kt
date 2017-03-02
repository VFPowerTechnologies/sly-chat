package io.slychat.messenger.core.persistence.sqlite.migrations

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.crypto.signal.randomPreKeyId
import io.slychat.messenger.core.persistence.sqlite.DatabaseMigration
import io.slychat.messenger.core.persistence.sqlite.TableCreationFailedException
import io.slychat.messenger.core.readResourceFileText

/** Initial database setup. */
class DatabaseMigrationInitial : DatabaseMigration(1) {
    companion object {
        /** Table names in creation order. */
        private val TABLE_NAMES = arrayListOf(
            "prekey_ids",
            "signed_prekeys",
            "unsigned_prekeys",
            "contacts",
            "conversation_info",
            "signal_sessions",
            "package_queue",
            "remote_contact_updates",
            "remote_group_updates",
            "send_message_queue",
            "groups",
            "group_members",
            "address_book_hashes",
            "expiring_messages",
            "event_log",
            "message_failures",
            "file_list_version",
            "files",
            "remote_file_updates",
            "uploads",
            "upload_parts"
        )
    }

    private fun initializePreKeyIds(connection: SQLiteConnection) {
        val nextSignedId = randomPreKeyId()
        val nextUnsignedId = randomPreKeyId()
        connection.exec("INSERT INTO prekey_ids (next_signed_id, next_unsigned_id) VALUES ($nextSignedId, $nextUnsignedId)")
    }

    private fun createTables(connection: SQLiteConnection) {
        for (tableName in TABLE_NAMES)
            createTable(connection, tableName)
    }

    private fun createTable(connection: SQLiteConnection, tableName: String) {
        val sql = javaClass.readResourceFileText("/schema/$tableName.sql")
        log.debug("Creating table {}", tableName)
        try {
            connection.exec(sql)
        }
        catch (t: Throwable) {
            log.error("Creation of table {} failed", tableName, t)
            throw TableCreationFailedException(tableName, t)
        }
    }


    override fun apply(connection: SQLiteConnection) {
        createTables(connection)
        initializePreKeyIds(connection)
        initializeFileListVersion(connection)
    }

    private fun initializeFileListVersion(connection: SQLiteConnection) {
        connection.exec("INSERT INTO file_list_version (version) VALUES (0)")
    }
}