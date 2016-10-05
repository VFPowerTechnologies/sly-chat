package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.testutils.withTempFile
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SQLitePersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    fun getRandomKey(): Key {
        val key = ByteArray(256/8)
        SecureRandom().nextBytes(key)
        return Key(key)
    }

    fun <R> withPersistenceManager(path: File?, params: SQLCipherParams?, body: (SQLitePersistenceManager) -> R): R {
        val persistenceManager = SQLitePersistenceManager(path, params)
        return try {
            persistenceManager.init()
            body(persistenceManager)
        }
        finally {
            persistenceManager.shutdown()
        }
    }

    fun testDecryption(key1: Key?, key2: Key?, shouldFail: Boolean) {
        val query = "CREATE TABLE IF NOT EXISTS t (i NUMBER)"

        val params1 = key1?.let {
            SQLCipherParams(DerivedKeySpec(it, HKDFInfoList.accountLocalInfo()), SQLCipherCipher.defaultCipher)
        }

        val params2 = key2?.let {
            SQLCipherParams(DerivedKeySpec(it, HKDFInfoList.accountLocalInfo()), SQLCipherCipher.defaultCipher)
        }

        withTempFile { dbPath ->
            withPersistenceManager(dbPath, params1) { persistenceManager ->
                persistenceManager.syncRunQuery { connection ->
                    connection.exec(query)
                }
            }

            val body: () -> Unit = {
                //getCurrentDatabaseVersion will throw
                withPersistenceManager(dbPath, params2) { persistenceManager ->
                    persistenceManager.syncRunQuery { connection ->
                        connection.exec(query)
                    }
                }
            }

            if (shouldFail) {
                val e = assertFailsWith(SQLiteException::class, body)
                assertEquals(SQLiteConstants.SQLITE_NOTADB, e.baseErrorCode)
            }
            else
                body()
        }

    }

    @Test
    fun `accessing an encrypted database without a key should fail`() {
        testDecryption(getRandomKey(), null, true)
    }

    @Test
    fun `accessing an encrypted database with the wrong key should fail`() {
        testDecryption(getRandomKey(), getRandomKey(), true)
    }

    @Test
    fun `accessing an encrypted database with the right key should success`() {
        val key = getRandomKey()
        testDecryption(key, key, false)
    }

    @Test
    fun `not providing a key should keep the database unencrypted`() {
        testDecryption(null, null, false)
    }

    @Test
    fun `contents of database should be initialized if database is newly created`() {
        //we can't use withTempFile here since that creates the file, which causes this to fail
        val tempDir = File(System.getProperty("java.io.tmpdir"))

        //for our purposes this should be fine
        val path = File(tempDir, randomUUID())

        try {
            //need an actual path for file existence check
            withPersistenceManager(path, null) { persistenceManager ->
                persistenceManager.syncRunQuery { connection ->
                    connection.prepare("SELECT * FROM contacts").use { stmt ->
                        while (stmt.step()) {}
                    }
                }
            }
        }
        finally {
            path.delete()
        }
    }
}