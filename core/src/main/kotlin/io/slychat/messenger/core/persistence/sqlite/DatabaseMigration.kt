package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class DatabaseMigration(
    val version: Int
) {
    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Override to modify the default migration procedure.
     *
     * 1) Applies /migrations/<version>/general.sql
     * 2) Applies /migrations/<version>/convo.sql
     **/
    open fun apply(connection: SQLiteConnection) {
        val general = getGeneralMigration(version)
        if (general != null)
            applyGeneralMigration(connection, version, general)

        val convo = getConvoMigration(version)
        if (convo != null)
            applyConvoMigration(connection, version, convo)
    }

    protected fun readResourceAsString(path: String): String? =
        javaClass.getResourceAsStream(path)?.use { it.reader(Charsets.UTF_8).readText() }

    protected fun getGeneralMigration(version: Int): String? =
        readResourceAsString("/migrations/$version/general.sql")

    protected fun getConvoMigration(version: Int): String? =
        readResourceAsString("/migrations/$version/convo.sql")

    /**
     * @param version Current database version number.
     */
    private fun applyGeneralMigration(connection: SQLiteConnection, version: Int, sql: String) {
        log.info("Applying general migration for {}", version)

        connection.exec(sql)
    }

    private fun applyConvoMigration(connection: SQLiteConnection, version: Int, sqlTemplate: String) {
        log.info("Applying convo migration for {}", version)

        ConversationTable.getConversationTableNames(connection).forEach { tableName ->
            log.info("Updating {}", tableName)
            val sql = sqlTemplate.replace("%tableName%", tableName)
            connection.exec(sql)
        }
    }
}