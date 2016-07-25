package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.readResourceFileText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

abstract class DatabaseMigration(
    val version: Int,
    val newTables: List<String> = ArrayList()
) {
    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Override to modify the default migration procedure.
     *
     * 1) Applies /migrations/<version>/general.sql
     * 2) Applies /migrations/<version>/convo.sql
     * 3) Creates named tables via /schema/<tableName>.sql
     **/
    open fun apply(connection: SQLiteConnection) {
        val general = getGeneralMigration(version)
        if (general != null)
            applyGeneralMigration(connection, version, general)

        val convo = getConvoMigration(version)
        if (convo != null)
            applyConvoMigration(connection, version, convo)

        createNewTables(connection)
    }

    protected fun createNewTables(connection: SQLiteConnection) {
        for (tableName in newTables)
            createTable(connection, tableName)
    }

    protected fun createTable(connection: SQLiteConnection, tableName: String) {
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