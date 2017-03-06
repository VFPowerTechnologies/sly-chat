package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

class TransferManagerImpl(
    override var options: TransferOptions,
    private val uploader: Uploader
) : TransferManager {
    override val events: Observable<TransferEvent>
        get() = uploader.events
    override val uploads: List<UploadStatus>
        get() = uploader.uploads

    override fun init() {
        uploader.init()
    }

    override fun shutdown() {
        uploader.shutdown()
    }

    override fun upload(info: UploadInfo): Promise<Unit, Exception> {
        return uploader.upload(info)
    }

    override fun clearError(uploadId: String): Promise<Unit, Exception> {
        return uploader.clearError(uploadId)
    }
}