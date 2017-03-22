package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.*
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
        it.withTransaction {
            fileUtils.insertFile(it, file)
        }
    }

    private fun getFileSelectQuery(startingAt: Int, count: Int, path: String?, includePending: Boolean): String {
        val whereClause = if (!includePending || path != null) {
            val clauses = ArrayList<String>()

            if (path != null)
                clauses.add("directory = ?")

            if (!includePending)
                clauses.add("is_pending = 0")

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
    file_size, mime_type
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

    override fun getFiles(startingAt: Int, count: Int, includePending: Boolean): Promise<List<RemoteFile>, Exception> = sqlitePersistenceManager.runQuery {
        val sql = getFileSelectQuery(startingAt, count, null, includePending)

        it.withPrepared(sql) {
            it.map { fileUtils.rowToRemoteFile(it) }
        }
    }

    private fun selectFiles(connection: SQLiteConnection, path: String, startingAt: Int, count: Int, includePending: Boolean): List<RemoteFile> {
        val sql = getFileSelectQuery(startingAt, count, path, includePending)

        return connection.withPrepared(sql) {
            it.bind(1, path)
            it.map { fileUtils.rowToRemoteFile(it) }
        }
    }

    override fun getFilesAt(startingAt: Int, count: Int, includePending: Boolean, path: String): Promise<List<RemoteFile>, Exception> = sqlitePersistenceManager.runQuery {
        selectFiles(it, path, startingAt, count, includePending)
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
    file_size = ?,
    mime_type = ?,
    is_pending = 0
WHERE
    id = ?
"""

        connection.withPrepared(sql) {
            fileUtils.remoteFileToRow(file, it)
            it.bind(15, file.id)
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

    private fun deleteFile(connection: SQLiteConnection, fileId: String) {
        //language=SQLite
        val sql = """
DELETE FROM
    files
WHERE
    id = ?
"""

        connection.withPrepared(sql) {
            it.bind(1, fileId)
            it.step()
        }
    }


    override fun deleteFiles(fileIds: List<String>): Promise<List<RemoteFile>, Exception> {
        if (fileIds.isEmpty())
            return Promise.ofSuccess(emptyList())

        return sqlitePersistenceManager.runQuery { connection ->
            //language=SQLite
            val sql = """
UPDATE
    files
SET
    is_deleted = 1
WHERE
    id = ?
"""

            val updated = ArrayList<RemoteFile>()

            connection.withTransaction {
                connection.withPrepared(sql) {
                    fileIds.forEach { fileId ->
                        it.bind(1, fileId)
                        it.step()
                        it.reset(true)

                        if (connection.changes <= 0)
                            throw InvalidFileException(fileId)

                        insertRemoteUpdate(connection, fileId, UPDATE_TYPE_DELETE)
                        val file = selectFile(connection, fileId)!!
                        fileUtils.updateIndexRemove(connection, file.userMetadata.directory)
                        updated.add(file)
                    }
                }

                fileUtils.cleanupIndex(connection)
            }

            updated
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

    private fun selectFile(connection: SQLiteConnection, fileId: String): RemoteFile? {
        //language=SQLite
        val sql = """
SELECT
    id, share_key, last_update_version,
    is_deleted, creation_date, modification_date,
    remote_file_size, file_key, file_name,
    directory, cipher_id, chunk_size,
    file_size, mime_type
FROM
    files
WHERE
    id = ?
"""

        return connection.withPrepared(sql) {
            it.bind(1, fileId)
            if (it.step())
                fileUtils.rowToRemoteFile(it)
            else
                null
        }
    }

    override fun getFile(fileId: String): Promise<RemoteFile?, Exception> = sqlitePersistenceManager.runQuery {
        selectFile(it, fileId)
    }

    override fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Long): Promise<FileListMergeResults, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val added = ArrayList<RemoteFile>()
            val deleted = ArrayList<RemoteFile>()
            val updated = ArrayList<RemoteFile>()

            var hadDeleted = false
            updates.forEach {
                //kinda bad but w/e
                if (isFilePresent(connection, it.id)) {
                    if (it.isDeleted) {
                        hadDeleted = true
                        fileUtils.updateIndexRemove(connection, it.userMetadata.directory)
                        deleteFile(connection, it.id)
                        deleted.add(it)
                    }
                    else {
                        updateFile(connection, it)
                        updated.add(it)
                    }
                }
                else {
                    fileUtils.insertFile(connection, it)
                    added.add(it)
                }
            }

            if (hadDeleted)
                fileUtils.cleanupIndex(connection)

            setVersion(connection, latestVersion)

            FileListMergeResults(added, deleted, updated)
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

    override fun removeRemoteUpdates(fileIds: List<String>): Promise<Unit, Exception> {
        if (fileIds.isEmpty())
            return Promise.ofSuccess(Unit)

        return sqlitePersistenceManager.runQuery { connection ->
            connection.withTransaction {
                connection.withPrepared("DELETE FROM remote_file_updates WHERE file_id=?") { stmt ->
                    fileIds.forEach { fileId ->
                        stmt.bind(1, fileId)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }
        }
    }

    override fun getFileCount(): Promise<Int, Exception> = sqlitePersistenceManager.runQuery {
        it.withPrepared("SELECT count(*) FROM files") { stmt ->
            stmt.step()
            stmt.columnInt(0)
        }
    }

    private fun getDirectoriesAt(connection: SQLiteConnection, path: String, startingAt: Int, count: Int): List<String> {
        //language=SQLite
        val sql = """
SELECT
    sub_dir
FROM
    directory_index
WHERE
    path = :path
ORDER BY
    sub_dir
LIMIT
    $count
OFFSET
    $startingAt
"""

        return connection.withPrepared(sql) {
            it.bind(":path", path)
            it.map { it.columnString(0) }
        }
    }

    //test use only
    internal fun getDirectoriesAt(path: String): Promise<List<String>, Exception> = sqlitePersistenceManager.runQuery {
        getDirectoriesAt(it, path, 0, 1000)
    }

    private fun getSubDirCount(connection: SQLiteConnection, path: String): Int {
        //language=SQLite
        val sql = """
SELECT
    count(*)
FROM
    directory_index
WHERE
    path = :path
"""

        return connection.withPrepared(sql) {
            it.bind(":path", path)
            it.step()
            it.columnInt(0)
        }
    }

    override fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<DirEntry>, Exception> = sqlitePersistenceManager.runQuery {
        val entries = ArrayList<DirEntry>()

        val prependPath = if (path == "/")
            ""
        else
            path

        val directories = getDirectoriesAt(it, path, startingAt, count)
        entries.addAll(directories.map {
            DirEntry.D("$prependPath/$it", it)
        })

        val remainingCount = count - entries.size

        if (remainingCount > 0) {
            val s = if (directories.isNotEmpty())
                0
            else
                startingAt - getSubDirCount(it, path)

            entries.addAll(
                selectFiles(it, path, s, remainingCount, true).map { DirEntry.F(it) }
            )
        }

        entries
    }
}