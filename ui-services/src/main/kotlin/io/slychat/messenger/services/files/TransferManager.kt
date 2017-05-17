package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.UploadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface TransferManager {
    val events: Observable<TransferEvent>

    val transfers: List<TransferStatus>

    fun init()

    fun shutdown()

    fun upload(info: UploadInfo): Promise<Unit, Exception>

    fun clearError(transferId: String): Promise<Unit, Exception>

    fun download(downloads: List<DownloadInfo>): Promise<Unit, Exception>

    fun cancel(transferIds: List<String>)

    fun remove(transferIds: List<String>): Promise<Unit, Exception>

    /** Removes transfers in COMPLETE or CANCELLED state. */
    fun removeCompleted(): Promise<Unit, Exception>
}

