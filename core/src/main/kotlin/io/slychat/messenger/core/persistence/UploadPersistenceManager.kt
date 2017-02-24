package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface UploadPersistenceManager {
    fun add(upload: Upload): Promise<Unit, Exception>

    fun setState(uploadId: String, newState: UploadState): Promise<Unit, Exception>

    fun completePart(uploadId : String, n: Int): Promise<Unit, Exception>

    fun setError(uploadId : String, error: UploadError?): Promise<Unit, Exception>

    fun getAll(): Promise<List<Upload>, Exception>

    fun get(uploadId : String): Promise<Upload?, Exception>
}