package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.test.withTempFile
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseMigrationTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    fun withTestDatabase(from: Int, to: Int, body: (SQLitePersistenceManager, SQLiteConnection) -> Unit) {
        val path = "/migration-db/%02dto%02d.db".format(from, to)
        javaClass.getResource(path) ?: throw RuntimeException("Missing migration test db: $path")

        withTempFile { file ->
            javaClass.getResourceAsStream(path).use { inputStream ->
                file.outputStream().use { inputStream.copyTo(it) }
            }

            val persistenceManager = SQLitePersistenceManager(file, null, null)
            try {
                //TODO control migration version
                persistenceManager.init()
                assertEquals(to, persistenceManager.currentDatabaseVersionSync(), "Invalid database version after init")
                val atomic = AtomicReference<Throwable>()
                persistenceManager.syncRunQuery { connection ->
                    //kovenant is hardcoded to use Exception as its error type for task/etc
                    //so everything else I wrote does this as well
                    //however AssertionError is an Error
                    //so this is a nasty hack for now
                    try {
                        body(persistenceManager, connection)
                    }
                    catch (t: Throwable) {
                        atomic.set(t)
                    }
                }

                val maybeException = atomic.get()
                if (maybeException != null)
                    throw maybeException
            }
            finally {
                persistenceManager.shutdown()
            }
        }
    }

    //XXX really low tech, but works
    fun assertColDef(connection: SQLiteConnection, tableName: String, colDef: String) {
        val sql = connection.prepare("""SELECT sql FROM sqlite_master WHERE type="table" and name=?""").use { stmt ->
            stmt.bind(1, tableName)
            if (!stmt.step())
                throw RuntimeException("Missing table: $tableName")
            stmt.columnString(0)
        }

        assertTrue(sql.contains(colDef, true), "Missing column def: $colDef")
    }

    @Test
    fun `migration 0 to 1`() {
        withTestDatabase(0, 1) { persistenceManager, connection ->
            ConversationTable.getConversationTableNames(connection).forEach { tableName ->
                assertColDef(connection, tableName, "received_timestamp INTEGER NOT NULL")

                connection.prepare("SELECT id, timestamp, received_timestamp FROM $tableName").use { stmt ->
                    stmt.foreach {
                        val id = stmt.columnString(0)
                        val timestamp = stmt.columnLong(1)
                        val receivedTimestamp = stmt.columnLong(2)

                        assertEquals(timestamp, receivedTimestamp, "Message id=$id has an invalid timestamp")
                    }
                }
            }
        }
    }
}