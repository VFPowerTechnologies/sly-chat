package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.services.StorageClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import rx.Observable
import rx.Scheduler

class DownloadOperationsImpl(
    private val authTokenManager: AuthTokenManager,
    private val storageClientFactory: StorageClientFactory,
    private val subscribeScheduler: Scheduler
) : DownloadOperations {
    override fun download(download: Download, file: RemoteFile): Observable<Long> {
        //XXX this isn't that great, but getting the retry stuff to work with Observables requires rewriting a bit of stuff
        return authFailureRetry(authTokenManager, Observable.create<Long> { subscriber ->
            val userCredentials = authTokenManager.map { it }.get()
            val op = DownloadOperation(userCredentials, download, file, storageClientFactory.create(), subscriber)
            op.run()
        }).subscribeOn(subscribeScheduler)
    }
}