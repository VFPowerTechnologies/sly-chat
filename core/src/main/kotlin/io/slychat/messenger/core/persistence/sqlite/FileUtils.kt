package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.DuplicateFilePathException

internal class FileUtils {
    fun rowToRemoteFile(stmt: SQLiteStatement, colOffset: Int = 0): RemoteFile {
        val userMetadata = UserMetadata(
            Key(stmt.columnBlob(colOffset + 7)),
            CipherId(stmt.columnInt(colOffset + 10).toShort()),
            stmt.columnString(colOffset + 9),
            stmt.columnString(colOffset + 8)
        )

        val isDeleted = stmt.columnBool(colOffset + 3)

        val fileMetadata = if (!isDeleted) {
            FileMetadata(
                stmt.columnLong(colOffset + 12),
                stmt.columnInt(colOffset + 11)
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
        stmt.bind(1, file.id)
        stmt.bind(2, file.shareKey)
        stmt.bind(3, file.lastUpdateVersion)
        stmt.bind(4, file.isDeleted)
        stmt.bind(5, file.creationDate)
        stmt.bind(6, file.modificationDate)
        stmt.bind(7, file.remoteFileSize)
        stmt.bind(8, file.userMetadata.fileKey.raw)
        stmt.bind(11, file.userMetadata.cipherId.short.toInt())
        stmt.bind(9, file.userMetadata.fileName)
        stmt.bind(10, file.userMetadata.directory)

        val fileMetadata = file.fileMetadata
        if (fileMetadata != null) {
            stmt.bind(12, fileMetadata.chunkSize)
            stmt.bind(13, fileMetadata.size)
        }
        else {
            stmt.bind(11, 0)
            stmt.bind(12, 0)
            stmt.bind(13, 0)
        }
    }

    fun insertFile(connection: SQLiteConnection, file: RemoteFile) {
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
        }
    }
}