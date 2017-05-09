package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface AttachmentCachePersistenceManager {
    fun getAllRequests(): Promise<List<AttachmentCacheRequest>, Exception>

    fun addRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception>

    fun updateRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception>

    //fileid
    fun deleteRequests(fileIds: List<String>): Promise<Unit, Exception>

    fun getZeroRefCountFiles(): Promise<List<String>, Exception>

//    fun incRefCount(fileIds: List<String>): Promise<Unit, Exception>
//
//    /** Returns subset of fileIds which have zero ref counts.*/
//    fun decRefCount(fileIds: List<String>): Promise<List<String>, Exception>
}