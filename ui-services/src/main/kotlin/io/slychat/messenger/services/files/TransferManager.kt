package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise
import rx.Observable

interface TransferManager {
    var options: TransferOptions

    val events: Observable<TransferEvent>

    val uploads: List<UploadStatus>

    fun init()

    fun shutdown()

    fun upload(request: UploadRequest): Promise<Unit, Exception>
}

