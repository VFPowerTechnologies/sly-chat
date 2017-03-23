package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.UploadInfo
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.bindUi
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

    private class ByType(val uploadIds: List<String>, val downloadIds: List<String>)

    private fun separateByType(transferIds: List<String>): ByType {
        val uploadIds = ArrayList<String>()
        val downloadIds = ArrayList<String>()

        transferIds.forEach {
            if (uploader.contains(it))
                uploadIds.add(it)
            else if (downloader.contains(it))
                downloadIds.add(it)
            else
                throw InvalidTransferException(it)
        }

        return ByType(uploadIds, downloadIds)
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

    override fun clearError(transferId: String): Promise<Unit, Exception> {
        return if (uploader.contains(transferId))
            uploader.clearError(transferId)
        else if (downloader.contains(transferId))
            downloader.clearError(transferId)
        else
            throw InvalidTransferException(transferId)
    }

    override fun download(info: DownloadInfo): Promise<Unit, Exception> {
        return downloader.download(info)
    }

    override fun cancel(transferIds: List<String>) {
        val s = separateByType(transferIds)
        s.downloadIds.forEach {
            downloader.cancel(it)
        }

        s.uploadIds.forEach {
            uploader.cancel(it)
        }
    }

    override fun remove(transferIds: List<String>): Promise<Unit, Exception> {
        val s = separateByType(transferIds)

        return downloader.remove(s.downloadIds) bindUi {
            uploader.remove(s.uploadIds)
        }
    }

    override fun removeCompleted(): Promise<Unit, Exception> {
        val downloadsToRemove = downloader.downloads.filter {
            it.state == TransferState.COMPLETE || it.state == TransferState.CANCELLED
        }.map { it.download.id }

        val uploadsToRemove = uploader.uploads.filter { it.state == TransferState.COMPLETE }.map { it.upload.id }

        return downloader.remove(downloadsToRemove) bindUi {
            uploader.remove(uploadsToRemove)
        }
    }
}