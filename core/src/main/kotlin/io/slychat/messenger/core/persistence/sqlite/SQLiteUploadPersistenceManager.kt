package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise

class SQLiteUploadPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : UploadPersistenceManager {
    private val fileUtils = FileUtils()

    private fun uploadToRow(upload: Upload, stmt: SQLiteStatement) {
        stmt.bind(1, upload.id)
        stmt.bind(2, upload.fileId)
        stmt.bind(3, upload.state)
        stmt.bind(4, upload.filePath)
        stmt.bind(5, upload.isEncrypted)
        stmt.bind(6, upload.error)
    }

    private fun rowToUpload(stmt: SQLiteStatement, parts: List<UploadPart>): Upload {
        val s = stmt.columnString(5)
        val error = if (s != null) {
            UploadError.valueOf(s)
        }
        else
            null

        return Upload(
            stmt.columnString(0),
            stmt.columnString(1),
            UploadState.valueOf(stmt.columnString(2)),
            stmt.columnString(3),
            stmt.columnBool(4),
            error,
            parts
        )
    }

    private fun uploadPartToRow(uploadId: String, part: UploadPart, stmt: SQLiteStatement) {
        stmt.bind(1, uploadId)
        stmt.bind(2, part.n)
        stmt.bind(3, part.offset)
        stmt.bind(4, part.localSize)
        stmt.bind(5, part.remoteSize)
        stmt.bind(6, part.isComplete)
    }

    private fun rowToUploadPart(stmt : SQLiteStatement): UploadPart {
        return UploadPart(
            stmt.columnInt(0),
            stmt.columnLong(1),
            stmt.columnLong(2),
            stmt.columnLong(3),
            stmt.columnBool(4)
        )
    }

    private fun insertUpload(connection: SQLiteConnection, upload: Upload) {
        //language=SQLite
        val sql = """
INSERT INTO
    uploads
    (id, file_id, state, file_path, is_encrypted, error)
VALUES
    (?, ?, ?, ?, ?, ?)
"""

        connection.withPrepared(sql) {
            uploadToRow(upload, it)
            it.step()
        }
    }

    private fun insertUploadParts(connection: SQLiteConnection, uploadId: String, parts: List<UploadPart>) {
        //language=SQLite
        val sql = """
INSERT INTO
    upload_parts
    (upload_id, n, offset, local_size, remote_size, is_complete)
VALUES
    (?, ?, ?, ?, ?, ?)
"""
        connection.batchInsert(sql, parts) { stmt, part ->
            uploadPartToRow(uploadId, part, stmt)
        }
    }

    override fun add(info: UploadInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery {
        val upload = info.upload
        it.withTransaction {
            fileUtils.insertFile(it, info.file)
            insertUpload(it, upload)
            insertUploadParts(it, upload.id, upload.parts)
        }
    }

    override fun setState(uploadId: String, newState: UploadState): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery {
        //language=SQLite
        val sql = """
UPDATE
    uploads
SET
    state = ?
WHERE
    id = ?
"""
        it.withPrepared(sql) {
            it.bind(1, newState)
            it.bind(2, uploadId)
            it.step()
        }

        if (it.changes <= 0)
            throw InvalidUploadException(uploadId)
    }

    override fun setError(uploadId: String, error: UploadError?): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery {
        //language=SQLite
        val sql = """
UPDATE
    uploads
SET
    error = ?
WHERE
    id = ?
"""

        it.withPrepared(sql) {
            it.bind(1, error)
            it.bind(2, uploadId)
            it.step()
        }

        if (it.changes <= 0)
            throw InvalidUploadException(uploadId)
    }

    override fun getAll(): Promise<List<UploadInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        //language=SQLite
        val sql = """
SELECT
    u.id, u.file_id, u.state, u.file_path, u.is_encrypted, u.error,

    f.id, f.share_key, f.last_update_version,
    f.is_deleted, f.creation_date, f.modification_date,
    f.remote_file_size, f.file_key, f.file_name,
    f.directory, f.cipher_id, f.chunk_size,
    f.file_size
FROM
    uploads AS u
JOIN
    files AS f
ON
    f.id = u.file_id
"""
        connection.withPrepared(sql) { stmt ->
            stmt.map {
                val id = stmt.columnString(0)
                val parts = getParts(connection, id)
                UploadInfo(
                    rowToUpload(stmt, parts),
                    fileUtils.rowToRemoteFile(stmt, 6)
                )
            }
        }
    }

    private fun getParts(connection: SQLiteConnection, uploadId: String): List<UploadPart> {
        //language=SQLite
        val sql = """
SELECT
    n, offset, local_size, remote_size, is_complete
FROM
    upload_parts
WHERE
    upload_id = ?
ORDER BY n
"""

        return connection.withPrepared(sql) {
            it.bind(1, uploadId)
            it.map { rowToUploadPart(it) }
        }
    }

    override fun get(uploadId: String): Promise<Upload?, Exception> = sqlitePersistenceManager.runQuery {
        val parts = getParts(it, uploadId)
        if (parts.isEmpty())
            null
        else {
            //language=SQLite
            val sql = """
SELECT
    id, file_id, state, file_path, is_encrypted, error
FROM
    uploads
WHERE
    id  = ?
"""

            it.withPrepared(sql) {
                it.bind(1, uploadId)
                if (it.step())
                    rowToUpload(it, parts)
                else
                    null
            }
        }
    }

    private fun isUploadPresent(connection: SQLiteConnection, uploadId: String): Boolean {
        val sql = """
SELECT
    1
FROM
    uploads
WHERE
    id = ?
"""

        return connection.withPrepared(sql) {
            it.bind(1, uploadId)
            it.step()
        }
    }

    override fun completePart(uploadId: String, n: Int): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery {
        if (!isUploadPresent(it, uploadId))
            throw InvalidUploadException(uploadId)

        //language=SQLite
        val sql = """
UPDATE
    upload_parts
SET
    is_complete = 1
WHERE
    upload_id = ?
AND
    n = ?
"""

        it.withPrepared(sql) {
            it.bind(1, uploadId)
            it.bind(2, n)
            it.step()
        }

        if (it.changes <= 0)
            throw InvalidUploadPartException(uploadId, n)
    }
}