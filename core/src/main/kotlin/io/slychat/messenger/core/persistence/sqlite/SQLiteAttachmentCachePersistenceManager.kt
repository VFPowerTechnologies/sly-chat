package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.persistence.AttachmentCachePersistenceManager
import io.slychat.messenger.core.persistence.AttachmentCacheRequest
import nl.komponents.kovenant.Promise

//TODO keep track of attachment references
//should be updated by message add/deletes, and we need to zero it when a file gets deleted I guess?
class SQLiteAttachmentCachePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : AttachmentCachePersistenceManager {
    override fun getAllRequests(): Promise<List<AttachmentCacheRequest>, Exception> = sqlitePersistenceManager.runQuery {
        //language=SQLite
        val sql = """
SELECT
    file_id, download_id
FROM
    attachment_cache_requests
"""
        it.withPrepared(sql) {
            it.map { AttachmentCacheRequest(it.columnString(0), it.columnString(1)) }
        }
    }

    override fun addRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception> {
        if (requests.isEmpty())
            return Promise.of(Unit)

        return sqlitePersistenceManager.runQuery {
            //language=SQLite
            val sql = """
INSERT INTO
    attachment_cache_requests
    (file_id, download_id)
VALUES
    (:fileId, :downloadId)
"""
            it.withTransaction {
                it.withPrepared(sql) { stmt ->
                    requests.forEach {
                        stmt.bind(":fileId", it.fileId)
                        stmt.bind(":downloadId", it.downloadId)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }
        }
    }

    override fun updateRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception> {
        if (requests.isEmpty())
            return Promise.of(Unit)

        return sqlitePersistenceManager.runQuery {
            //language=SQLite
            val sql = """
UPDATE
    attachment_cache_requests
SET
    download_id = :downloadId
WHERE
    file_id = :fileId
"""

            it.withTransaction {
                it.withPrepared(sql) { stmt ->
                    requests.forEach {
                        stmt.bind(":downloadId", it.downloadId)
                        stmt.bind(":fileId", it.fileId)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }
        }
    }

    override fun deleteRequests(fileIds: List<String>): Promise<Unit, Exception> {
        if (fileIds.isEmpty())
            return Promise.of(Unit)

        return sqlitePersistenceManager.runQuery {
            //language=SQLite
            val sql = """
DELETE FROM
    attachment_cache_requests
WHERE
    file_id = :fileId
"""

            it.withTransaction {
                it.withPrepared(sql) { stmt ->
                    fileIds.forEach {
                        stmt.bind(":fileId", it)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }
        }
    }

    override fun getZeroRefCountFiles(): Promise<List<String>, Exception> {
        TODO()
    }
}
