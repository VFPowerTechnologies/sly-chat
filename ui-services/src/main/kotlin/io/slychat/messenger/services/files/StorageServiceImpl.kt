package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

//XXX an issue right now is that upload adds a file to the list but it isn't reflected in the file list until the upload completes
//I guess this isn't that much of an issue anyways
class StorageServiceImpl(
    private val authTokenManager: AuthTokenManager,
    private val storageClient: StorageAsyncClient,
    private val fileListPersistenceManager: FileListPersistenceManager,
    networkStatus: Observable<Boolean>
) : StorageService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val quotaSubject = BehaviorSubject.create<Quota>()

    override val quota: Observable<Quota>
        get() = quotaSubject

    private val updatesSubject = PublishSubject.create<List<RemoteFile>>()

    override val updates: Observable<List<RemoteFile>>
        get() = updatesSubject

    private var subscription: Subscription? = null

    private var isNetworkAvailable = false

    private var isUpdatingQuota = false

    init {
        subscription = networkStatus.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isNetworkAvailable)
            updateQuota()
    }

    fun updateQuota() {
        if (isUpdatingQuota)
            return

        isUpdatingQuota = true

        authTokenManager.bind {
            storageClient.getQuota(it)
        } successUi {
            isUpdatingQuota = false
            quotaSubject.onNext(it)
        } failUi  {
            isUpdatingQuota = false
            log.error("Failed to update quota: {}", it.message, it)
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }

    override fun sync() {
        TODO()
    }

    override fun getFileList(): Promise<List<RemoteFile>, Exception> {
        return fileListPersistenceManager.getAllFiles()
    }

    override fun getFileListFor(path: String): Promise<List<RemoteFile>, Exception> {
        TODO()
    }

    override fun deleteFile(): Promise<Unit, Exception> {
        TODO()
    }

    override fun getFileListVersion(): Promise<Int, Exception> {
        TODO()
    }
}