package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.isNotNetworkError
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
    private val syncJobFactory: StorageSyncJobFactory,
    networkStatus: Observable<Boolean>
) : StorageService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val quotaSubject = BehaviorSubject.create<Quota>()

    override val quota: Observable<Quota>
        get() = quotaSubject

    private val updatesSubject = PublishSubject.create<List<RemoteFile>>()

    override val updates: Observable<List<RemoteFile>>
        get() = updatesSubject

    private val syncRunningSubject = BehaviorSubject.create(false)

    override val syncRunning: Observable<Boolean>
        get() = syncRunningSubject

    private val isSyncRunning: Boolean
        get() = syncRunningSubject.value

    private var subscription: Subscription? = null

    private var isNetworkAvailable = false

    private var isUpdatingQuota = false

    init {
        subscription = networkStatus.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isNetworkAvailable) {
            updateQuota()
            sync()
        }
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

    private fun updateSyncStatus(v: Boolean) {
        syncRunningSubject.onNext(v)
    }

    override fun init() {
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }

    override fun sync() {
        if (isSyncRunning || !isNetworkAvailable)
            return

        updateSyncStatus(true)

        authTokenManager.bind {
            syncJobFactory.create(it).run()
        } successUi {
            log.info("Sync job complete: remoteUpdatesPerformed={}; newListVersion={}", it.remoteUpdatesPerformed, it.newListVersion)
            updateSyncStatus(false)
        } fail {
            log.condError(isNotNetworkError(it), "Sync job failed: {}", it.message, it)
            updateSyncStatus(false)
        }
    }

    override fun getFile(fileId: String): Promise<RemoteFile?, Exception> {
        return fileListPersistenceManager.getFile(fileId)
    }

    override fun getFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception> {
        return fileListPersistenceManager.getFiles(startingAt, count, false)
    }

    override fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception> {
        return fileListPersistenceManager.getFilesAt(startingAt, count, false, path)
    }

    override fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception> {
        return fileListPersistenceManager.deleteFiles(fileIds) successUi {
            sync()
        }
    }
}