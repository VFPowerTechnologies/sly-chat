package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface Uploader {
    var simulUploads: Int

    var isNetworkAvailable: Boolean

    val events: Observable<TransferEvent>

    val uploads: List<UploadStatus>

    fun init()

    fun shutdown()

    fun upload(info: UploadInfo): Promise<Unit, Exception>

    fun clearError(uploadId: String): Promise<Unit, Exception>

    fun remove(uploadIds: List<String>): Promise<Unit, Exception>
}