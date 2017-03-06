package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise
import rx.Observable

interface Downloader {
    var simulDownloads: Int

    val events: Observable<TransferEvent>

    val downloads: List<DownloadStatus>

    fun init()

    fun shutdown()

    fun download(fileId: String, decrypt: Boolean, toPath: String): Promise<Unit, Exception>
}