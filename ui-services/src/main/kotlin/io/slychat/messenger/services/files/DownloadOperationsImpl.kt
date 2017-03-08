package io.slychat.messenger.services.files

import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.services.StorageClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import rx.Observable
import rx.Scheduler
import java.util.*
import java.util.concurrent.TimeUnit

class DownloadOperationsImpl(
    private val authTokenManager: AuthTokenManager,
    private val storageClientFactory: StorageClientFactory,
    private val subscribeScheduler: Scheduler
) : DownloadOperations {
    override fun download(download: Download, file: RemoteFile): Observable<Long> {
        //XXX this isn't that great, but getting the retry stuff to work with Observables requires rewriting a bit of stuff
        return Observable.create<Long> { subscriber ->
            val userCredentials = authTokenManager.map { it }.get()
            val op = DownloadOperation(userCredentials, download, file, storageClientFactory.create(), subscriber)
            op.run()
        }.retryWhen {
            val maxRetries = 3
            it.zipWith(Observable.range(1, maxRetries + 1), { e, i -> e to i }).flatMap {
                val (e, i) = it

                if (i > maxRetries)
                    Observable.error(e)
                else {
                    if (e is UnauthorizedException) {
                        authTokenManager.invalidateToken()
                        val exp = Math.pow(2.0, i.toDouble())
                        val secs = Random().nextInt(exp.toInt() + 1).toLong()
                        Observable.timer(secs, TimeUnit.SECONDS)
                    }
                    else
                        Observable.error(e)
                }
            }
        }.subscribeOn(subscribeScheduler)
    }
}