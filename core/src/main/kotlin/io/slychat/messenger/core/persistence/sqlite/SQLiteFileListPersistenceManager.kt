package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.persistence.FileListUpdate
import io.slychat.messenger.core.persistence.InvalidFileException
import nl.komponents.kovenant.Promise
import java.util.*

class SQLiteFileListPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : FileListPersistenceManager {
    companion object {
        private const val UPDATE_TYPE_DELETE = "d"
        private const val UPDATE_TYPE_METADATA = "m"
    }

    private val fileUtils = FileUtils()

    private fun isFilePresent(connection: SQLiteConnection, fileId: String): Boolean {
        val sql = """
SELECT
    1
FROM
    files
WHERE
    id = ?
"""

        return connection.withPrepared(sql) {
            it.bind(1, fileId)
            it.step()
        }
    }

    override fun addFile(file: RemoteFile): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery {
        fileUtils.insertFile(it, file)
    }

    private fun getFileSelectQuery(startingAt: Int, count: Int, path: String?, includePending: Boolean): String {
        val whereClause = if (!includePending || path != null) {
            val clauses = ArrayList<String>()

            if (path != null)
                clauses.add("directory = ?")

            if (!includePending)
                clauses.add("last_update_version != 0")

            "WHERE " + clauses.joinToString(" AND ")
        }
        else
            ""

        //language=SQLite
        return """
SELECT
    id, share_key, last_update_version,
    is_deleted, creation_date, modification_date,
    remote_file_size, file_key, file_name,
    directory, cipher_id, chunk_size,
    file_size
FROM
    files
$whereClause
ORDER BY
    id
LIMIT
    $count
OFFSET
    $startingAt
"""

    }

    override fun getAllFiles(startingAt: Int, count: Int, includePending: Boolean): Promise<List<RemoteFile>, Exception> = sqlitePersistenceManager.runQuery {
        val sql = getFileSelectQuery(startingAt, count, null, includePending)

        it.withPrepared(sql) {
            it.map { fileUtils.rowToRemoteFile(it) }
        }
    }

    override fun getFilesAt(startingAt: Int, count: Int, includePending: Boolean, path: String): Promise<List<RemoteFile>, Exception> = sqlitePersistenceManager.runQuery {
        val sql = getFileSelectQuery(startingAt, count, path, includePending)

        it.withPrepared(sql) {
            it.bind(1, path)
            it.map { fileUtils.rowToRemoteFile(it) }
        }
    }

    private fun updateFile(connection: SQLiteConnection, file: RemoteFile) {
        //language=SQLite
        val sql = """
UPDATE
    files
SET
    id = ?,
    share_key = ?,
    last_update_version = ?,
    is_deleted = ?,
    creation_date = ?,
    modification_date = ?,
    remote_file_size = ?,
    file_key = ?,
    file_name = ?,
    directory = ?,
    cipher_id = ?,
    chunk_size = ?,
    file_size = ?
WHERE
    id = ?
"""

        connection.withPrepared(sql) {
            fileUtils.remoteFileToRow(file, it)
            it.bind(14, file.id)
            it.step()
        }

        //FIXME
        if (connection.changes <= 0)
            throw IllegalStateException("File ${file.id} doesn't exist")
    }

    //currently all remote updates are exclusive with each other (since it doesn't make sense to delete something, then rename it, etc)
    private fun insertRemoteUpdate(connection: SQLiteConnection, fileId: String, type: String) {
        //language=SQLite
        val sql = """
INSERT OR REPLACE INTO
    remote_file_updates
    (file_id, type)
VALUES
    (?, ?)
"""

        connection.withPrepared(sql) {
            it.bind(1, fileId)
            it.bind(2, type)
            it.step()
        }
    }

    override fun deleteFile(fileId: String): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        //language=SQLite
        val sql = """
UPDATE
    files
SET
    is_deleted = 1
WHERE
    id = ?
"""

        connection.withTransaction {
            connection.withPrepared(sql) {
                it.bind(1, fileId)
                it.step()
            }

            if (connection.changes <= 0)
                throw InvalidFileException(fileId)

            insertRemoteUpdate(connection, fileId, UPDATE_TYPE_DELETE)
        }
    }

    override fun updateMetadata(fileId: String, userMetadata: UserMetadata): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        //language=SQLite
        val sql = """
UPDATE
    files
SET
    file_name = ?,
    directory = ?
WHERE
    id = ?
"""
        connection.withTransaction {
            connection.withPrepared(sql) {
                it.bind(1, userMetadata.fileName)
                it.bind(2, userMetadata.directory)
                it.bind(3, fileId)
                it.step()
            }

            if (connection.changes <= 0)
                throw InvalidFileException(fileId)

            insertRemoteUpdate(connection, fileId, UPDATE_TYPE_METADATA)
        }
    }

    override fun getFileInfo(fileId: String): Promise<RemoteFile?, Exception> = sqlitePersistenceManager.runQuery {
        //language=SQLite
        val sql = """
SELECT
    id, share_key, last_update_version,
    is_deleted, creation_date, modification_date,
    remote_file_size, file_key, file_name,
    directory, cipher_id, chunk_size,
    file_size
FROM
    files
WHERE
    id = ?
"""

        it.withPrepared(sql) {
            it.bind(1, fileId)
            if (it.step())
                fileUtils.rowToRemoteFile(it)
            else
                null
        }
    }

    override fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Long): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            updates.forEach {
                //kinda bad but w/e
                if (isFilePresent(connection, it.id))
                    updateFile(connection, it)
                else
                    fileUtils.insertFile(connection, it)
            }

            setVersion(connection, latestVersion)
        }
    }

    private fun setVersion(connection: SQLiteConnection, latestVersion: Long) {
        //language=SQLite
        val sql = """
UPDATE
    file_list_version
SET
    version = ?
"""

        connection.withPrepared(sql) {
            it.bind(1, latestVersion)
            it.step()
        }
    }

    override fun getVersion(): Promise<Long, Exception> = sqlitePersistenceManager.runQuery {
        it.withPrepared("SELECT version FROM file_list_version") {
            it.step()

            it.columnLong(0)
        }
    }

    override fun getRemoteUpdates(): Promise<List<FileListUpdate>, Exception> = sqlitePersistenceManager.runQuery {
        //language=SQLite
        val sql = """
SELECT
    u.file_id,
    u.type,
    f.file_key,
    f.cipher_id,
    f.file_name,
    f.directory
FROM
    remote_file_updates u
JOIN
    files f
ON
    f.id = u.file_id
"""
       it.withPrepared(sql) {
           it.map { rowToFileListUpdate(it) }
       }
    }

    private fun rowToFileListUpdate(stmt: SQLiteStatement): FileListUpdate {
        val fileId = stmt.columnString(0)
        val type = stmt.columnString(1)

        return when (type) {
            UPDATE_TYPE_DELETE -> FileListUpdate.Delete(fileId)

            UPDATE_TYPE_METADATA -> {
                val userMetadata = UserMetadata(
                    Key(stmt.columnBlob(2)),
                    CipherId(stmt.columnInt(3).toShort()),
                    stmt.columnString(5),
                    stmt.columnString(4)
                )
                FileListUpdate.MetadataUpdate(fileId, userMetadata)
            }

            else -> throw IllegalArgumentException("Unknown FileListUpdate type: $type")
        }
    }
}