package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise
import rx.Observable

interface Downloader {
    var simulDownloads: Int

    val events: Observable<TransferEvent>

    fun download(fileId: String, decrypt: Boolean, toPath: String): Promise<Unit, Exception>
}