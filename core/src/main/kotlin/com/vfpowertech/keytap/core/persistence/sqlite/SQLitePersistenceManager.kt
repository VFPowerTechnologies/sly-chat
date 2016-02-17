package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteJob
import com.almworks.sqlite4java.SQLiteQueue
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.randomPreKeyId
import com.vfpowertech.keytap.core.persistence.PersistenceManager
import com.vfpowertech.keytap.core.readResourceFileText
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.task
import org.slf4j.LoggerFactory
import java.io.File

/** The latest database version number. */
private val LATEST_DATABASE_VERSION = 0

/** Table names in creation order. */
private val TABLE_NAMES = arrayListOf(
    "prekey_ids",
    "signed_prekeys",
    "unsigned_prekeys",
    "contacts"
)

/**
 * Lazily initialized at time of first query.
 *
 * @param path Pass in null for an in-memory database.
 */
class SQLitePersistenceManager(
    private val path: File?,
    private val localDataEncryptionKey: ByteArray,
    private val localDataEncryptionParams: CipherParams?
) : PersistenceManager {
    private lateinit var sqliteQueue: SQLiteQueue
    private var initialized = false
    private val logger = LoggerFactory.getLogger(javaClass)

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
     * Open the connection to the database.
     *
     * If the database doesn't exist, it's created and then initialized to the latest version.
     * If it exists, and it's version is behind the latest, an attempt to upgrade it is performed.
     * Otherwise initialization finishes.
     */
    override fun init() {
        if (initialized)
            return

        sqliteQueue = SQLiteQueue(path)
        sqliteQueue.start()

        val create = !(path?.exists() ?: false)

        sqliteQueue.execute(object : SQLiteJob<Unit>() {
            override fun job(connection: SQLiteConnection): Unit {
                if (!create) {
                    val version = getCurrentDatabaseVersion(connection)
                    if (version == LATEST_DATABASE_VERSION) {
                        logger.debug("Database is up to date")
                        return
                    }
                    else {
                        logger.info("Performing migration from version {} to {}", version, LATEST_DATABASE_VERSION)
                        //TODO
                        //if number isn't update to date, apply migrations for each missing version in turn
                        //run each migration within a transaction, ending with updating the user_version (within the transaction)
                        //use savepoints so if the upgrade fails we can rollback completely? seems pointless though
                        throw UnsupportedOperationException()
                    }
                }
                else {
                    initializeDatabase(connection)
                }
                return Unit
            }
        }).get()

        initialized = true
    }

    override fun initAsync(): Promise<Unit, Exception> = task {
        init()
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
    fun <R> runQuery(body: (connection: SQLiteConnection) -> R): Promise<R, Exception> =
        if (!initialized)
            initAsync() bind { realRunQuery(body) }
        else
            realRunQuery(body)
}