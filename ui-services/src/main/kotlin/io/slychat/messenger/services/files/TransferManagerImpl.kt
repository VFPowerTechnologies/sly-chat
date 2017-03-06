package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

class TransferManagerImpl(
    private val uploader: Uploader,
    private val downloader: Downloader
) : TransferManager {
    override val events: Observable<TransferEvent> = Observable.merge(uploader.events, downloader.events)

    override val uploads: List<UploadStatus>
        get() = uploader.uploads

    override var options: TransferOptions
        get() = TransferOptions(downloader.simulDownloads, uploader.simulUploads)
        set(value) {
            downloader.simulDownloads = value.simulDownloads
            uploader.simulUploads = value.simulUploads
        }

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

    override fun download(fileId: String, decrypt: Boolean, toPath: String): Promise<Unit, Exception> {
        TODO()
    }
}