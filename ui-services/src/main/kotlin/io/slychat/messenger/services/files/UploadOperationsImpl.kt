package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.services.UploadClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.Scheduler

class UploadOperationsImpl(
    private val fileAccess: PlatformFileAccess,
    private val authTokenManager: AuthTokenManager,
    private val uploadClientFactory: UploadClientFactory,
    private val keyVault: KeyVault,
    private val subscribeScheduler: Scheduler
) : UploadOperations {
    override fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception> {
        return authTokenManager.map {
            val op = CreateUploadOperation(it, upload, file, keyVault, uploadClientFactory.create())
            op.run()
        }
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile): Observable<Long> {
        return authFailureRetry(authTokenManager, Observable.create<Long> { subscriber ->
            try {
                val userCredentials = authTokenManager.map { it }.get()
                val op = UploadPartOperation(fileAccess, userCredentials, upload, part, file, uploadClientFactory.create(), subscriber)
                op.run()

                subscriber.onCompleted()
            }
            catch (t: Throwable) {
                subscriber.onError(t)
            }
        }).subscribeOn(subscribeScheduler)
    }
}