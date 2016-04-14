package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.vfpowertech.keytap.core.test.withTempFile
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

    fun getRandomKey(): ByteArray {
        val key = ByteArray(256/8)
        SecureRandom().nextBytes(key)
        return key
    }

    fun <R> withPersistenceManager(path: File, key: ByteArray?, body: (SQLitePersistenceManager) -> R): R {
        val persistenceManager = SQLitePersistenceManager(path, key, null)
        return try {
            persistenceManager.init()
            body(persistenceManager)
        }
        finally {
            persistenceManager.shutdown()
        }
    }

    fun testDecryption(key1: ByteArray?, key2: ByteArray?, shouldFail: Boolean) {
        val query = "CREATE TABLE IF NOT EXISTS t (i NUMBER)"

        withTempFile { dbPath ->
            withPersistenceManager(dbPath, key1) { persistenceManager ->
                persistenceManager.syncRunQuery { connection ->
                    connection.exec(query)
                }
            }

            val body: () -> Unit = {
                //getCurrentDatabaseVersion will throw
                withPersistenceManager(dbPath, key2) { persistenceManager ->
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
}