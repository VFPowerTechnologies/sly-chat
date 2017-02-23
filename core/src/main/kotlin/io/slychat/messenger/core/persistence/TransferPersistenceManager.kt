package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

//used by TransferManager to persist transfer state data
interface TransferPersistenceManager {
    fun markUploadPartComplete(uploadId: String, partN: Int): Promise<Unit, Exception>

    fun markUploadComplete(uploadId: String): Promise<Unit, Exception>
}