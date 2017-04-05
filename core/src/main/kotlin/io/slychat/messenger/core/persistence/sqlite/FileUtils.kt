package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.SharedFrom
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.DuplicateFilePathException

internal class FileUtils {
    fun rowToRemoteFile(stmt: SQLiteStatement, colOffset: Int = 0): RemoteFile {
        val sharedFromUserId = stmt.columnNullableUserId(14)
        val sharedFrom = sharedFromUserId?.let {
            SharedFrom(it, stmt.columnNullableGroupId(15))
        }

        val userMetadata = UserMetadata(
            Key(stmt.columnBlob(colOffset + 7)),
            CipherId(stmt.columnInt(colOffset + 10).toShort()),
            stmt.columnString(colOffset + 9),
            stmt.columnString(colOffset + 8),
            sharedFrom
        )

        val isDeleted = stmt.columnBool(colOffset + 3)

        val fileMetadata = if (!isDeleted) {
            FileMetadata(
                stmt.columnLong(colOffset + 12),
                stmt.columnInt(colOffset + 11),
                stmt.columnString(colOffset + 13)
            )
        }
        else
            null

        return RemoteFile(
            stmt.columnString(colOffset + 0),
            stmt.columnString(colOffset + 1),
            stmt.columnLong(colOffset + 2),
            isDeleted,
            userMetadata,
            fileMetadata,
            stmt.columnLong(colOffset + 4),
            stmt.columnLong(colOffset + 5),
            stmt.columnLong(colOffset + 6)
        )
    }

    fun remoteFileToRow(file: RemoteFile, stmt: SQLiteStatement) {
        stmt.bind(":id", file.id)
        stmt.bind(":shareKey", file.shareKey)
        stmt.bind(":lastUpdateVersion", file.lastUpdateVersion)
        stmt.bind(":isDeleted", file.isDeleted)
        stmt.bind(":creationDate", file.creationDate)
        stmt.bind(":modificationDate", file.modificationDate)
        stmt.bind(":remoteFileSize", file.remoteFileSize)

        val userMetadata = file.userMetadata
        stmt.bind(":fileKey", userMetadata.fileKey)
        stmt.bind(":cipherId", userMetadata.cipherId)
        stmt.bind(":fileName", userMetadata.fileName)
        stmt.bind(":directory", userMetadata.directory)

        if (userMetadata.sharedFrom != null) {
            stmt.bind(":sharedFromUserId", userMetadata.sharedFrom.userId)
            stmt.bind(":sharedFromGroupId", userMetadata.sharedFrom.groupId)
        }

        val fileMetadata = file.fileMetadata
        if (fileMetadata != null) {
            stmt.bind(":chunkSize", fileMetadata.chunkSize)
            stmt.bind(":fileSize", fileMetadata.size)
            stmt.bind(":mimeType", fileMetadata.mimeType)
        }
        else {
            stmt.bind(":chunkSize", 0)
            stmt.bind(":fileSize", 0)
            stmt.bindNull(":mimeType")
        }
    }

    fun insertFile(connection: SQLiteConnection, file: RemoteFile) {
        val isPending = if (file.isPending) 1 else 0
        //language=SQLite
        val sql = """
INSERT INTO
    files
    (
    id, share_key, last_update_version,
    is_deleted, creation_date, modification_date,
    remote_file_size, file_key, file_name,
    shared_from_user_id, shared_from_group_id,
    directory, cipher_id, chunk_size,
    file_size, mime_type, is_pending
    )
    VALUES
    (
    :id, :shareKey, :lastUpdateVersion,
    :isDeleted, :creationDate, :modificationDate,
    :remoteFileSize, :fileKey, :fileName,
    :sharedFromUserId, :sharedFromGroupId,
    :directory, :cipherId, :chunkSize,
    :fileSize, :mimeType, $isPending
    )
"""
        connection.withPrepared(sql) {
            remoteFileToRow(file, it)
            try {
                it.step()
            }
            catch (e: SQLiteException) {
                if (e.errorCode != SQLiteConstants.SQLITE_CONSTRAINT_UNIQUE)
                    throw e
                val message = e.message ?: throw e

                if (message.contains("files.directory, files.file_name"))
                    throw DuplicateFilePathException(file.userMetadata.directory, file.userMetadata.fileName)

                throw e
            }

            updateIndexAdd(connection, file.userMetadata.directory)
        }
    }

    fun deleteFile(connection: SQLiteConnection, file: RemoteFile) {
        //language=SQLite
        val sql = """
DELETE FROM
    files
WHERE
    id = ?
"""

        connection.withPrepared(sql) {
            it.bind(1, file.id)
            it.step()
        }

        updateIndexRemove(connection, file.userMetadata.directory)
    }

    fun selectFile(connection: SQLiteConnection, fileId: String): RemoteFile? {
        //language=SQLite
        val sql = """
SELECT
    id, share_key, last_update_version,
    is_deleted, creation_date, modification_date,
    remote_file_size, file_key, file_name,
    directory, cipher_id, chunk_size,
    file_size, mime_type, shared_from_user_id,
    shared_from_group_id
FROM
    files
WHERE
    id = ?
"""

        return connection.withPrepared(sql) {
            it.bind(1, fileId)
            if (it.step())
                rowToRemoteFile(it)
            else
                null
        }
    }


    internal fun withPathComponents(path: String, body: (parentPath:String, subDir: String) -> Unit)  {
        if (path == "/")
            return

        val components = path.split('/')
        val n = components.size

        for (i in 1..(n - 1)) {
            val p = components.subList(0, i).joinToString("/")
            val parent = if (p.isEmpty())
                "/"
            else
                p
            val sub = components[i]

            body(parent, sub)
        }
    }

    fun updateIndexAdd(connection: SQLiteConnection, path: String) {
        //sqlite has nothing like ON DUPLICATE KEY UPDATE
        //language=SQLite
        val sql = """
INSERT OR REPLACE INTO
    directory_index
    (path, sub_dir, ref_count)
VALUES
    (:path, :subDir, coalesce((SELECT ref_count + 1 FROM directory_index WHERE path = :path AND sub_dir = :subDir), 1))
"""
        connection.withPrepared(sql) { stmt ->
            withPathComponents(path) { parent, sub ->
                stmt.bind(":path", parent)
                stmt.bind(":subDir", sub)
                stmt.step()
                stmt.reset()
            }
        }
    }

    fun cleanupIndex(connection: SQLiteConnection) {
        //language=SQLite
        val sql = """
DELETE FROM
    directory_index
WHERE
    ref_count = 0
"""
        connection.withPrepared(sql, SQLiteStatement::step)
    }

    fun updateIndexRemove(connection: SQLiteConnection, path: String) {
        //language=SQLite
        val sql = """
UPDATE
    directory_index
SET
    ref_count = ref_count - 1
WHERE
    path = :path
AND
    sub_dir = :subDir
"""
        connection.withPrepared(sql) { stmt ->
            withPathComponents(path) { parent, sub ->
                stmt.bind(":path", parent)
                stmt.bind(":subDir", sub)
                stmt.step()
                stmt.reset()
            }
        }
    }
}