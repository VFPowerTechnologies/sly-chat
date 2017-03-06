package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface TransferManager {
    var options: TransferOptions

    val events: Observable<TransferEvent>

    val uploads: List<UploadStatus>

    fun init()

    fun shutdown()

    fun upload(info: UploadInfo): Promise<Unit, Exception>

    fun clearError(uploadId: String): Promise<Unit, Exception>

    fun download(fileId: String, decrypt: Boolean, toPath: String): Promise<Unit, Exception>
}

