package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteJob
import com.almworks.sqlite4java.SQLiteQueue
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.persistence.PersistenceManager
import io.slychat.messenger.core.persistence.sqlite.migrations.DatabaseMigrationInitial
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import java.io.File

/** The latest database version number. */
private val LATEST_DATABASE_VERSION = 9

/** Just used to wrap Errors thrown when running SQLite jobs. */
class SQLitePersistenceManagerErrorException(e: Error) : RuntimeException("Uncaught Error in job", e)

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
    private data class InitializationResult(val initWasRequired: Boolean, val freshDatabase: Boolean)

    private lateinit var sqliteQueue: SQLiteQueue
    private var initialized = false
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(localDataEncryptionKey == null || localDataEncryptionKey.size == 256/8) {
            "SQLCipher encryption key must be 256bit, got a ${localDataEncryptionKey!!.size*8}bit key instead"
        }
    }

    /**
     * Responsible for initial database creation.
     *
     * Member function only to access javaClass. Logging aside, does not otherwise read or modify instance state.
     */
    private fun initializeDatabase(connection: SQLiteConnection) {
        connection.withTransaction {
            DatabaseMigrationInitial().apply(connection)
            connection.exec("PRAGMA user_version = $LATEST_DATABASE_VERSION")
        }
    }

    private fun setCurrentDatabaseVersion(connection: SQLiteConnection, version: Int) {
        connection.exec("PRAGMA user_version = $version")
    }

    private fun getCurrentDatabaseVersion(connection: SQLiteConnection): Int {
        return connection.prepare("PRAGMA user_version").use { stmt ->
            stmt.step()
            stmt.columnInt(0)
        }
    }

    fun currentDatabaseVersion(): Promise<Int, Exception> = runQuery { getCurrentDatabaseVersion(it) }
    fun currentDatabaseVersionSync(): Int = currentDatabaseVersion().get()

    private fun enableForeignKeys(connection: SQLiteConnection) {
        connection.exec("PRAGMA foreign_keys = ON")
    }

    /**
     * Initialize the worker queue.
     *
     * @return False if already initialized, true otherwise.
     */
    private fun initQueue(): InitializationResult {
        if (initialized)
            return InitializationResult(false, false)

        //this is here because I'm an idiot and shoulda set the initial database version to 1 from zero; when using
        //temp files, the path exists but it still needs to create the contents
        val created = if (path == null)
            true
        else {
            if (path.exists())
                path.length() == 0L
            else
                true
        }

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
    private fun initContents(freshDatabase: Boolean, latestVersion: Int): Promise<Unit, Exception> {
        return realRunQuery { connection ->
            if (!freshDatabase) {
                val version = getCurrentDatabaseVersion(connection)
                if (version == latestVersion) {
                    logger.debug("Database is up to date")
                }
                else {
                    logger.info("Performing migration from version {} to {}", version, latestVersion)
                    migrateDatabase(connection, version, latestVersion)
                }
            }
            else {
                initializeDatabase(connection)
            }

            //we need to init this after any migrations, as the pragma can't be modified within a transaction, and with
            //fks on any referenced tables will be updated to point to the old renamed table, which makes it so we can't
            //recreate tables referenced in a fk relationship without rebuilding every referencing table
            enableForeignKeys(connection)
        }
    }

    private fun getMigrationObject(version: Int): DatabaseMigration {
        try {
            val cls = Class.forName("io.slychat.messenger.core.persistence.sqlite.migrations.DatabaseMigration$version")
            return cls.newInstance() as DatabaseMigration
        }
        catch (e: ClassNotFoundException) {
            throw RuntimeException("No migration found for version=$version")
        }
    }

    /**
     * Applies incremental migrations from one version to another.
     */
    private fun migrateDatabase(connection: SQLiteConnection, from: Int, to: Int) {
        //XXX maybe we should use a savepoint to undo the entire conversion if migration fails along the way?
        for (version in from..to-1) {
            connection.withTransaction {
                getMigrationObject(version).apply(connection)
                setCurrentDatabaseVersion(connection, version+1)
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
        init(LATEST_DATABASE_VERSION)
    }

    /** Used to cause migrations to only be run up to a certain version. Used in tests only. */
    internal fun init(latestVersion: Int) {
        val initResult = initQueue()
        if (!initResult.initWasRequired)
            return
        initContents(initResult.freshDatabase, latestVersion).get()
    }

    override fun initAsync(): Promise<Unit, Exception> {
        val initResult = initQueue()

        if (!initResult.initWasRequired)
            return Promise.ofSuccess(Unit)

        return initContents(initResult.freshDatabase, LATEST_DATABASE_VERSION)
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
                catch (e: Error) {
                    deferred.reject(SQLitePersistenceManagerErrorException(e))
                }

                return Unit
            }
        })

        return deferred.promise
    }

    /** Wrapper around running an SQLiteJob, passing the result or failure into a Promise. */
    fun <R> runQuery(body: (connection: SQLiteConnection) -> R): Promise<R, Exception> {
        //require(initialized) { "runQuery called before initialization" }

        return realRunQuery(body)
    }

    /** Blocks until query is complete. Just wraps runQuery and calls get() on the resulting promise. */
    fun <R> syncRunQuery(body: (connection: SQLiteConnection) -> R): R =
        runQuery(body).get()
}