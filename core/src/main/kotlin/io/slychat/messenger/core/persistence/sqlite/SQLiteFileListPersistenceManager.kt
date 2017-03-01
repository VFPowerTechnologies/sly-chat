package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import nl.komponents.kovenant.Promise

class SQLiteFileListPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : FileListPersistenceManager {
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
                fileUtils.rowToRemoteFile(it)
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
                    fileUtils.insertFile(connection, it)
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