package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.UploadInfo
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subscriptions.CompositeSubscription

class TransferManagerImpl(
    private val userConfigService: UserConfigService,
    private val uploader: Uploader,
    private val downloader: Downloader,
    networkStatus: Observable<Boolean>
) : TransferManager {
    override val events: Observable<TransferEvent> = Observable.merge(uploader.events, downloader.events)

    override val uploads: List<UploadStatus>
        get() = uploader.uploads

    override val downloads: List<DownloadStatus>
        get() = downloader.downloads

    private var subscriptions = CompositeSubscription()

    init {
        subscriptions += networkStatus.subscribe {
            uploader.isNetworkAvailable = it
            downloader.isNetworkAvailable = it
        }

        downloader.simulDownloads = userConfigService.tranfersSimulDownloads

        uploader.simulUploads = userConfigService.transfersSimulUploads

        subscriptions += userConfigService.updates.subscribe { onUserConfigUpdates(it) }
    }

    private fun onUserConfigUpdates(keys: Collection<String>) {
        keys.forEach {
            when (it) {
                UserConfig.TRANSFERS_SIMUL_UPLOADS ->
                    uploader.simulUploads = userConfigService.transfersSimulUploads

                UserConfig.TRANSFERS_SIMUL_DOWNLOADS ->
                    downloader.simulDownloads = userConfigService.tranfersSimulDownloads
            }
        }
    }

    override fun init() {
        uploader.init()
        downloader.init()
    }

    override fun shutdown() {
        subscriptions.clear()

        uploader.shutdown()
        downloader.shutdown()
    }

    override fun upload(info: UploadInfo): Promise<Unit, Exception> {
        return uploader.upload(info)
    }

    override fun clearUploadError(uploadId: String): Promise<Unit, Exception> {
        return uploader.clearError(uploadId)
    }

    override fun clearDownloadError(downloadId: String): Promise<Unit, Exception> {
        return downloader.clearError(downloadId)
    }

    override fun download(info: DownloadInfo): Promise<Unit, Exception> {
        return downloader.download(info)
    }

    override fun cancelDownload(downloadId: String): Boolean {
        return downloader.cancel(downloadId)
    }

    override fun removeDownloads(downloadIds: List<String>): Promise<Unit, Exception> {
        return downloader.remove(downloadIds)
    }

    override fun removeUploads(uploadIds: List<String>): Promise<Unit, Exception> {
        return uploader.remove(uploadIds)
    }
}