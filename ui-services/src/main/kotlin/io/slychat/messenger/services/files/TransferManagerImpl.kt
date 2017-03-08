package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.Subscription

class TransferManagerImpl(
    private val uploader: Uploader,
    private val downloader: Downloader,
    networkStatus: Observable<Boolean>
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

    private var subscription: Subscription? = null

    init {
        subscription = networkStatus.subscribe {
            uploader.isNetworkAvailable = it
            downloader.isNetworkAvailable = it
        }
    }

    override fun init() {
        uploader.init()
        downloader.init()
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null

        uploader.shutdown()
        downloader.shutdown()
    }

    override fun upload(info: UploadInfo): Promise<Unit, Exception> {
        return uploader.upload(info)
    }

    override fun clearError(uploadId: String): Promise<Unit, Exception> {
        return uploader.clearError(uploadId)
    }

    override fun download(info: DownloadInfo): Promise<Unit, Exception> {
        return downloader.download(info)
    }

    override fun cancelDownload(downloadId: String): Boolean {
        return downloader.cancel(downloadId)
    }
}