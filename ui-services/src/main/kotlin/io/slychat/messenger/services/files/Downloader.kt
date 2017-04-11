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

    fun download(downloads: List<DownloadInfo>): Promise<Unit, Exception>

    //throws IllegalStateException if called on an active download
    fun remove(downloadIds: List<String>): Promise<Unit, Exception>

    fun cancel(downloadId: String)

    fun clearError(downloadId: String): Promise<Unit, Exception>

    fun contains(transferId: String): Boolean
}