package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.services.StorageClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import rx.Observable
import rx.Scheduler
import java.util.concurrent.atomic.AtomicBoolean

class DownloadOperationsImpl(
    private val fileAccess: PlatformFileAccess,
    private val authTokenManager: AuthTokenManager,
    private val storageClientFactory: StorageClientFactory,
    private val subscribeScheduler: Scheduler
) : DownloadOperations {
    override fun download(download: Download, file: RemoteFile, isCancelled: AtomicBoolean): Observable<Long> {
        //XXX this isn't that great, but getting the retry stuff to work with Observables requires rewriting a bit of stuff
        return authFailureRetry(authTokenManager, Observable.create<Long> { subscriber ->
            try {
                val userCredentials = authTokenManager.map { it }.get()
                val op = DownloadOperation(fileAccess, userCredentials, download, file, storageClientFactory.create(), subscriber, isCancelled)
                op.run()

                subscriber.onCompleted()
            }
            catch (t: Throwable) {
                subscriber.onError(t)
            }
        }).subscribeOn(subscribeScheduler)
    }

    override fun deleteFile(download: Download): Promise<Unit, Exception> {
        return task {
            fileAccess.delete(download.filePath)
        }
    }
}