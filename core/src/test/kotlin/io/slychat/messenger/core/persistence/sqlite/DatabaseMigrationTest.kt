package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteException
import io.slychat.messenger.testutils.withTempFile
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
                persistenceManager.init(to)
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

    fun assertTableExists(connection: SQLiteConnection, tableName: String) {
        try {
            connection.prepare("SELECT 1 FROM $tableName").use { stmt ->
                stmt.step()
            }
        }
        catch (e: SQLiteException) {
            if (e.message?.contains("no such table") ?: false)
                throw AssertionError("Table $tableName is missing")
            else
                throw e
        }
    }

    fun assertTableNotExists(connection: SQLiteConnection, tableName: String) {
        try {
            connection.prepare("SELECT 1 FROM $tableName").use { stmt ->
                stmt.step()
            }
            throw AssertionError("Table $tableName exists")
        }
        catch (e: SQLiteException) {
            if (e.message?.contains("no such table") ?: false)
                return
            else
                throw e
        }

    }

    fun check0To1(persistenceManager: SQLitePersistenceManager, connection: SQLiteConnection) {
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

    @Test
    fun `migration 0 to 1`() {
        withTestDatabase(0, 1) { persistenceManager, connection ->
            check0To1(persistenceManager, connection)
        }
    }

    fun check1To2(persistenceManager: SQLitePersistenceManager, connection: SQLiteConnection) {
        assertTableExists(connection, "package_queue")
    }

    @Test
    fun `migration 1 to 2`() {
        withTestDatabase(1, 2) { persistenceManager, connection ->
            check1To2(persistenceManager, connection)
        }
    }

    fun check2To3(persistenceManager: SQLitePersistenceManager, connection: SQLiteConnection) {
        assertColDef(connection, "contacts", "is_pending INTEGER NOT NULL")
    }

    @Test
    fun `migration 2 to 3`() {
        withTestDatabase(2, 3) { persistenceManager, connection ->
            check2To3(persistenceManager, connection)
        }
    }

    fun check3To4(persistenceManager: SQLitePersistenceManager, connection: SQLiteConnection) {
        assertTableExists(connection, "remote_contact_updates")
    }

    @Test
    fun `migration 3 to 4`() {
        withTestDatabase(3, 4) { persistenceManager, connection ->
            check3To4(persistenceManager, connection)
        }
    }

    fun assertTableRowCount(connection: SQLiteConnection, tableName: String, rowCount: Int) {
        connection.withPrepared("SELECT count(1) FROM $tableName") { stmt ->
            stmt.step()
            val n = stmt.columnInt(0)
            //make sure we discarded the old contact session
            assertEquals(rowCount, n, "Invalid number of rows")
        }
    }

    fun check4To5(persistenceManager: SQLitePersistenceManager, connection: SQLiteConnection) {
        //check conversion + old table drop
        assertTableNotExists(connection, "signal_sessions_old")

        assertTableRowCount(connection, "signal_sessions", 2)

        connection.withPrepared("SELECT contact_id, device_id, session FROM signal_sessions WHERE contact_id=154") { stmt ->
            stmt.foreach {
                assertEquals(154, stmt.columnLong(0), "Invalid user id")
                assertEquals(1, stmt.columnInt(1), "Invalid device id")
                assertTrue(stmt.columnBlob(2).size != 0, "Empty session")
            }
        }
    }

    @Test
    fun `migration 4 to 5`() {
        withTestDatabase(4, 5) { persistenceManager, connection ->
            check4To5(persistenceManager, connection)
        }
    }

    fun check5To6(persistenceManager: SQLitePersistenceManager, connection: SQLiteConnection) {
        assertTableRowCount(connection, "conversation_info", 1)
        assertColDef(connection, "conversation_info", "FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE")

        //TODO make this process generic somehow for future migrations

        connection.withPrepared("SELECT contact_id, unread_count, last_message, last_timestamp FROM conversation_info") { stmt ->
            stmt.step()

            assertEquals(154, stmt.columnLong(0), "Invalid contact_id")
            assertEquals(0, stmt.columnInt(1), "Invalid unread_count")
            assertEquals("Test", stmt.columnString(2), "Invalid last_message")
            assertEquals(1467047660422, stmt.columnLong(3), "Invalid last_timestamp")
        }
    }

    @Test
    fun `migration 5 to 6`() {
        withTestDatabase(5, 6) { persistenceManager, connection ->
            check5To6(persistenceManager, connection)
        }
    }
}