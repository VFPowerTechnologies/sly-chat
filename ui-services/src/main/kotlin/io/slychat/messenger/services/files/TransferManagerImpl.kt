package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.UploadInfo
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*

class TransferManagerImpl(
    private val userConfigService: UserConfigService,
    private val uploader: Uploader,
    private val downloader: Downloader,
    networkStatus: Observable<Boolean>
) : TransferManager {
    override val events: Observable<TransferEvent> = Observable.merge(uploader.events, downloader.events)

    override val transfers: List<TransferStatus>
        get() {
            val statuses = ArrayList<TransferStatus>()

            uploader.uploads.mapTo(statuses) { TransferStatus(Transfer.U(it.upload), it.file, it.state, UploadTransferProgress(it.progress, it.transferedBytes, it.totalBytes)) }
            downloader.downloads.mapTo(statuses) { TransferStatus(Transfer.D(it.download), it.file, it.state, it.progress) }

            return statuses
        }

    override val quota: Observable<Quota>
        get() = uploader.quota

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

    override fun removeCompletedDownloads(): Promise<Unit, Exception> {
        val toRemove = downloader.downloads.filter {
            it.state == TransferState.COMPLETE || it.state == TransferState.CANCELLED
        }.map { it.download.id }
        return downloader.remove(toRemove)
    }

    override fun removeCompletedUploads(): Promise<Unit, Exception> {
        val toRemove = uploader.uploads.filter { it.state == TransferState.COMPLETE }.map { it.upload.id }
        return uploader.remove(toRemove)
    }
}