package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface AttachmentCachePersistenceManager {
    fun getAllRequests(): Promise<List<AttachmentCacheRequest>, Exception>

    fun addRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception>

    fun updateRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception>

    //fileid
    fun deleteRequests(fileIds: List<String>): Promise<Unit, Exception>

    /** Returns all file ids with zero ref count. */
    fun getZeroRefCountFiles(): Promise<List<String>, Exception>

    fun deleteZeroRefCountEntries(fileIds: List<String>): Promise<Unit, Exception>
}