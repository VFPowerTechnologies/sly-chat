package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.DownloadPersistenceManager
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject

class DownloaderImpl(
    override var simulDownloads: Int,
    private val downloadPersistenceManager: DownloadPersistenceManager,
    networkStatus: Observable<Boolean>
) : Downloader {
    private val subject = PublishSubject.create<TransferEvent>()

    override val events: Observable<TransferEvent>
        get() = subject

    override fun download(fileId: String, decrypt: Boolean, toPath: String): Promise<Unit, Exception> {
        TODO()
    }
}