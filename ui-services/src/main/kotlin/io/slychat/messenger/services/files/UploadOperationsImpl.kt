package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.NewUploadResponse
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.core.rx.observable
import io.slychat.messenger.services.UploadClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.files.cache.AttachmentCache
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.Scheduler
import java.util.concurrent.atomic.AtomicBoolean

class UploadOperationsImpl(
    private val fileAccess: PlatformFileAccess,
    private val authTokenManager: AuthTokenManager,
    private val uploadClientFactory: UploadClientFactory,
    private val keyVault: KeyVault,
    private val subscribeScheduler: Scheduler,
    private val attachmentCache: AttachmentCache
) : UploadOperations {
    override fun create(upload: Upload, file: RemoteFile): Promise<NewUploadResponse, Exception> {
        return authTokenManager.map {
            val op = CreateUploadOperation(it, upload, file, keyVault, uploadClientFactory.create())
            op.run()
        }
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, isCancelled: AtomicBoolean): Observable<Long> {
        return authFailureRetry(authTokenManager, Observable.create<Long> { subscriber ->
            try {
                val userCredentials = authTokenManager.map { it }.get()
                val op = UploadPartOperation(fileAccess, userCredentials, upload, part, file, uploadClientFactory.create(), subscriber, isCancelled)
                op.run()

                subscriber.onCompleted()
            }
            catch (t: Throwable) {
                subscriber.onError(t)
            }
        }).subscribeOn(subscribeScheduler)
    }

    override fun complete(upload: Upload): Promise<Unit, Exception> {
        return authTokenManager.map {
            val op = CompeteUploadOperation(it, upload, uploadClientFactory.create())
            op.run()
        }
    }

    override fun cache(upload: Upload, file: RemoteFile, isCancelled: AtomicBoolean): Observable<Long> {
        return observable<Long> {
            CacheFileOperation(fileAccess, upload, file, it, isCancelled, attachmentCache).run()
        }.subscribeOn(subscribeScheduler)
    }

    override fun cancel(upload: Upload): Promise<Unit, Exception> {
        return authTokenManager.map {
            val op = CancelUploadOperation(it, upload, uploadClientFactory.create())
            op.run()
        }
    }
}
