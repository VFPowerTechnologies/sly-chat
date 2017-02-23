package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise
import rx.Observable
import java.io.File

sealed class TransferEvent {
    class Added
    class Removed
    class StatusChange
}

data class TransferOptions(
    val simulDownloads: Int,
    val simulUploads: Int
)

//handles actual client thread pools/etc
//needs a HttpClientFactory; we should probably add a method to HttpClient for uploads/downloads
//we actually want this to reflect all queued transfers
interface TransferManager {
    var options: TransferOptions

    val events: Observable<TransferEvent>

    //encrypt=false is used when uploading stuff from the local cache
    fun upload(path: File, encrypt: Boolean): Promise<Unit, Exception>
}

