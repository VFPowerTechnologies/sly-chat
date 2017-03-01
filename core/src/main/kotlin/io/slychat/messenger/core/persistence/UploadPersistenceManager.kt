package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile
import nl.komponents.kovenant.Promise

data class UploadInfo(
    val upload: Upload,
    val file: RemoteFile
)

interface UploadPersistenceManager {
    fun add(info: UploadInfo): Promise<Unit, Exception>

    fun setState(uploadId: String, newState: UploadState): Promise<Unit, Exception>

    fun completePart(uploadId : String, n: Int): Promise<Unit, Exception>

    fun setError(uploadId : String, error: UploadError?): Promise<Unit, Exception>

    fun getAll(): Promise<List<UploadInfo>, Exception>

    fun get(uploadId : String): Promise<Upload?, Exception>
}