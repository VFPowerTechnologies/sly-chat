package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import nl.komponents.kovenant.Promise

class SQLiteFileListPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : FileListPersistenceManager {
    private fun rowToRemoteFile(stmt: SQLiteStatement): RemoteFile {
        val userMetadata = UserMetadata(
            Key(stmt.columnBlob(7)),
            stmt.columnString(9),
            stmt.columnString(8)
        )

        val isDeleted = stmt.columnBool(3)

        val fileMetadata = if (!isDeleted) {
            FileMetadata(
                stmt.columnLong(12),
                CipherId(stmt.columnInt(10).toShort()),
                stmt.columnInt(11)
            )
        }
        else
            null

        return RemoteFile(
            stmt.columnString(0),
            stmt.columnString(1),
            stmt.columnInt(2),
            isDeleted,
            userMetadata,
            fileMetadata,
            stmt.columnLong(4),
            stmt.columnLong(5),
            stmt.columnLong(6)
        )
    }

    private fun remoteFileToRow(file: RemoteFile, stmt: SQLiteStatement) {
        stmt.bind(1, file.id)
        stmt.bind(2, file.shareKey)
        stmt.bind(3, file.lastUpdateVersion)
        stmt.bind(4, file.isDeleted)
        stmt.bind(5, file.creationDate)
        stmt.bind(6, file.modificationDate)
        stmt.bind(7, file.remoteFileSize)
        stmt.bind(8, file.userMetadata.fileKey.raw)
        stmt.bind(9, file.userMetadata.fileName)
        stmt.bind(10, file.userMetadata.directory)

        val fileMetadata = file.fileMetadata
        if (fileMetadata != null) {
            stmt.bind(11, fileMetadata.cipherId.short.toInt())
            stmt.bind(12, fileMetadata.chunkSize)
            stmt.bind(13, fileMetadata.size)
        }
        else {
            stmt.bind(11, 0)
            stmt.bind(12, 0)
            stmt.bind(13, 0)
        }
    }

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

    private fun insertFile(connection: SQLiteConnection, file: RemoteFile) {
        //language=SQLite
        val sql = """
INSERT INTO
    files
    (
    id, share_key, last_update_version,
    is_deleted, creation_date, modification_date,
    remote_file_size, file_key, file_name,
    directory, cipher_id, chunk_size,
    file_size
    )
    VALUES
    (
    ?, ?, ?,
    ?, ?, ?,
    ?, ?, ?,
    ?, ?, ?,
    ?
    )
"""
        connection.withPrepared(sql) {
            remoteFileToRow(file, it)
            it.step()
        }
    }

    override fun addFile(file: RemoteFile): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery {
        insertFile(it, file)
    }

    override fun getAllFiles(): Promise<List<RemoteFile>, Exception> = sqlitePersistenceManager.runQuery {
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
"""

        it.withPrepared(sql) {
            it.map { rowToRemoteFile(it) }
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
            remoteFileToRow(file, it)
            it.bind(14, file.id)
            it.step()
        }

        //FIXME
        if (connection.changes <= 0)
            throw IllegalStateException("File ${file.id} doesn't exist")

    }

    override fun updateFile(file: RemoteFile): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateFile(connection, file)
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
                rowToRemoteFile(it)
            else
                null
        }
    }

    override fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Int): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            updates.forEach {
                //kinda bad but w/e
                if (isFilePresent(connection, it.id))
                    updateFile(connection, it)
                else
                    insertFile(connection, it)
            }

            setVersion(connection, latestVersion)
        }
    }

    private fun setVersion(connection: SQLiteConnection, latestVersion: Int) {
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

    override fun getVersion(): Promise<Int, Exception> = sqlitePersistenceManager.runQuery {
        it.withPrepared("SELECT version FROM file_list_version") {
            it.step()

            it.columnInt(0)
        }
    }
}