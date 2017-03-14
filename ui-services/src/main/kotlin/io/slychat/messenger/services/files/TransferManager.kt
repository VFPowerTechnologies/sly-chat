package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface TransferManager {
    val events: Observable<TransferEvent>

    val uploads: List<UploadStatus>

    val downloads: List<DownloadStatus>

    fun init()

    fun shutdown()

    fun upload(info: UploadInfo): Promise<Unit, Exception>

    fun clearUploadError(uploadId: String): Promise<Unit, Exception>

    fun clearDownloadError(downloadId: String): Promise<Unit, Exception>

    fun download(info: DownloadInfo): Promise<Unit, Exception>

    fun cancelDownload(downloadId: String): Boolean

    fun removeDownloads(downloadIds: List<String>): Promise<Unit, Exception>

    fun removeUploads(uploadIds: List<String>): Promise<Unit, Exception>
}

