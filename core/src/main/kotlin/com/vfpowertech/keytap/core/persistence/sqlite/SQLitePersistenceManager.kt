package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteJob
import com.almworks.sqlite4java.SQLiteQueue
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.randomPreKeyId
import com.vfpowertech.keytap.core.persistence.PersistenceManager
import com.vfpowertech.keytap.core.readResourceFileText
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import java.io.File

/** The latest database version number. */
private val LATEST_DATABASE_VERSION = 0

/** Table names in creation order. */
private val TABLE_NAMES = arrayListOf(
    "prekey_ids",
    "signed_prekeys",
    "unsigned_prekeys",
    "contacts",
    "conversation_info",
    "signal_sessions"
)

private data class InitializationResult(val initWasRequired: Boolean, val freshDatabase: Boolean)

//localDataEncryptionParams don't work too well... they contain an IV, which wouldn't be reused
//for the db, we also can't control cipher params anyways
//for storing files, the iv would be per-block (no chaining blocks else we can't provide seek; is this an issue?)

/**
 * Must be initialized prior to use. Once initialized, methods may be called from any thread.
 *
 * @param path Pass in null for an in-memory database.
 */
class SQLitePersistenceManager(
    private val path: File?,
    private val localDataEncryptionKey: ByteArray?,
    private val localDataEncryptionParams: CipherParams?
) : PersistenceManager {
    private lateinit var sqliteQueue: SQLiteQueue
    private var initialized = false
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(localDataEncryptionKey == null || localDataEncryptionKey.size == 256/8) {
            "SQLCipher encryption key must be 256bit, got a ${localDataEncryptionKey!!.size*8}bit key instead"
        }
    }

    private fun initializePreKeyIds(connection: SQLiteConnection) {
        val nextSignedId = randomPreKeyId()
        val nextUnsignedId = randomPreKeyId()
        connection.exec("INSERT INTO prekey_ids (next_signed_id, next_unsigned_id) VALUES ($nextSignedId, $nextUnsignedId)")
    }

    /**
     * Responsible for initial database creation.
     *
     * Member function only to access javaClass. Logging aside, does not otherwise read or modify instance state.
     */
    private fun initializeDatabase(connection: SQLiteConnection) {
        connection.withTransaction {
            for (tableName in TABLE_NAMES) {
                val sql = javaClass.readResourceFileText("/schema/$tableName.sql")
                logger.debug("Creating table {}", tableName)
                try {
                    connection.exec(sql)
                }
                catch (t: Throwable) {
                    logger.error("Creation of table {} failed", tableName, t)
                    throw TableCreationFailedException(tableName, t)
                }
            }

            initializePreKeyIds(connection)

            connection.exec("PRAGMA user_version = $LATEST_DATABASE_VERSION")
        }
    }

    private fun getCurrentDatabaseVersion(connection: SQLiteConnection): Int {
        val stmt = connection.prepare("PRAGMA user_version")
        return stmt.use { stmt ->
            stmt.step()
            stmt.columnInt(0)
        }
    }

    /**
     * Initialize the worker queue.
     *
     * @return False if already initialized, true otherwise.
     */
    private fun initQueue(): InitializationResult {
        if (initialized)
            return InitializationResult(false, false)

        val created = !(path?.exists() ?: false)

        sqliteQueue = SQLiteQueue(path)
        sqliteQueue.start()

        val encryptionKey = localDataEncryptionKey
        if (encryptionKey != null) {
            realRunQuery { connection ->
                connection.exec("""PRAGMA key = "x'${encryptionKey.hexify()}'"""")
            }.get()
        }

        initialized = true
        return InitializationResult(true, created)
    }

    /** Initialize new database or migrate existing database. Should be run off the main thread. */
    private fun initContents(freshDatabase: Boolean): Promise<Unit, Exception> {
        return realRunQuery { connection ->
            if (!freshDatabase) {
                val version = getCurrentDatabaseVersion(connection)
                if (version == LATEST_DATABASE_VERSION) {
                    logger.debug("Database is up to date")
                }
                else {
                    logger.info("Performing migration from version {} to {}", version, LATEST_DATABASE_VERSION)
                    //TODO
                    //if number isn't update to date, apply migrations for each missing version in turn
                    //run each migration within a transaction, ending with updating the user_version (within the transaction)
                    //use savepoints so if the upgrade fails we can rollback completely? seems pointless though
                    //make sure to upgrade all conv_* tables as well
                    throw UnsupportedOperationException()
                }
            }
            else {
                initializeDatabase(connection)
            }
        }
    }

    /**
     * Open the connection to the database.
     *
     * If the database doesn't exist, it's created and then initialized to the latest version.
     * If it exists, and it's version is behind the latest, an attempt to upgrade it is performed.
     * Otherwise initialization finishes.
     */
    override fun init() {
        val initResult = initQueue()
        if (!initResult.initWasRequired)
            return
        initContents(initResult.freshDatabase).get()
    }

    override fun initAsync(): Promise<Unit, Exception> {
        val initResult = initQueue()

        if (!initResult.initWasRequired)
            return Promise.ofSuccess(Unit)

        return initContents(initResult.freshDatabase)
    }

    override fun shutdown() {
        if (initialized) {
            sqliteQueue.stop(true).join()
            initialized = false
        }
    }

    private fun <R> realRunQuery(body: (connection: SQLiteConnection) -> R): Promise<R, Exception> {
        val deferred = deferred<R, Exception>()
        sqliteQueue.execute(object : SQLiteJob<Unit>() {
            override fun job(connection: SQLiteConnection): Unit {
                try {
                    deferred.resolve(body(connection))
                }
                catch (e: Exception) {
                    deferred.reject(e)
                }

                return Unit
            }
        })

        return deferred.promise
    }

    /** Wrapper around running an SQLiteJob, passing the result or failure into a Promise. */
    fun <R> runQuery(body: (connection: SQLiteConnection) -> R): Promise<R, Exception> {
        require(initialized) { "runQuery called before initialization" }

        return realRunQuery(body)
    }

    /** Blocks until query is complete. Just wraps runQuery and calls get() on the resulting promise. */
    fun <R> syncRunQuery(body: (connection: SQLiteConnection) -> R): R =
        runQuery(body).get()
}