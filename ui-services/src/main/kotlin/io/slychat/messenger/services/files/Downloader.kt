package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.DownloadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface Downloader {
    var simulDownloads: Int

    var isNetworkAvailable: Boolean

    val events: Observable<TransferEvent>

    val downloads: List<DownloadStatus>

    fun init()

    fun shutdown()

    fun download(info: DownloadInfo): Promise<Unit, Exception>

    fun clearError(downloadId: String): Promise<Unit, Exception>
}