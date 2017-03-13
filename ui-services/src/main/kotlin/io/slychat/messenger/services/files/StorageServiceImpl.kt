package io.slychat.messenger.services.files

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.io.FileNotFoundException

//XXX an issue right now is that upload adds a file to the list but it isn't reflected in the file list until the upload completes
//I guess this isn't that much of an issue anyways
class StorageServiceImpl(
    private val authTokenManager: AuthTokenManager,
    private val storageClient: StorageAsyncClient,
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val syncJobFactory: StorageSyncJobFactory,
    private val transferManager: TransferManager,
    private val fileAccess: PlatformFileAccess,
    networkStatus: Observable<Boolean>
) : StorageService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val quotaSubject = BehaviorSubject.create<Quota>()

    override val quota: Observable<Quota>
        get() = quotaSubject

    private val updatesSubject = PublishSubject.create<List<RemoteFile>>()

    override val updates: Observable<List<RemoteFile>>
        get() = updatesSubject

    private val syncEventsSubject = BehaviorSubject.create<FileListSyncEvent>()

    override val syncEvents: Observable<FileListSyncEvent>
        get() = syncEventsSubject

    private var isSyncRunning = false

    override val transferEvents: Observable<TransferEvent>
        get() = transferManager.events

    private var subscription: Subscription? = null

    private var isNetworkAvailable = false

    private var isUpdatingQuota = false

    override val uploads: List<UploadStatus>
        get() = transferManager.uploads

    override val downloads: List<DownloadStatus>
        get() = transferManager.downloads

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

    private fun beginSync() {
        isSyncRunning = true
        syncEventsSubject.onNext(FileListSyncEvent.Begin())
    }

    private fun endSync(result: StorageSyncResult?) {
        isSyncRunning = false
        val ev = if (result != null)
            FileListSyncEvent.End(result)
        else
            FileListSyncEvent.Error()

        syncEventsSubject.onNext(ev)
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

        beginSync()

        authTokenManager.bind {
            syncJobFactory.create(it).run()
        } successUi {
            log.info("Sync job complete: remoteUpdatesPerformed={}; newListVersion={}", it.remoteUpdatesPerformed, it.newListVersion)
            endSync(it)
        } fail {
            log.condError(isNotNetworkError(it), "Sync job failed: {}", it.message, it)
            endSync(null)
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

    override fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String): Promise<Unit, Exception> {
        return fileAccess.getFileInfo(localFilePath) bindUi { fileInfo ->
            val cipher = CipherList.defaultDataEncryptionCipher
            val key = generateKey(cipher.keySizeBits)

            val userMetadata = UserMetadata(
                key,
                cipher.id,
                remoteFileDirectory,
                remoteFileName
            )

            //FIXME calc based on file size
            val chunkSize = 128.kb
            val remoteFileSize = getRemoteFileSize(cipher, fileInfo.size, chunkSize)

            val parts = calcUploadParts(cipher, fileInfo.size, chunkSize, MIN_PART_SIZE)

            val fileMetadata = FileMetadata(
                fileInfo.size,
                chunkSize,
                fileInfo.mimeType
            )

            val file = RemoteFile(
                generateFileId(),
                generateShareKey(),
                0,
                false,
                userMetadata,
                fileMetadata,
                currentTimestamp(),
                currentTimestamp(),
                remoteFileSize
            )

            val upload = Upload(
                generateUploadId(),
                file.id,
                UploadState.PENDING,
                fileInfo.displayName,
                localFilePath,
                false,
                null, parts
            )

            val info = UploadInfo(
                upload,
                file
            )

            transferManager.upload(info)
        }
    }

    override fun downloadFile(fileId: String, localFilePath: String): Promise<Unit, Exception> {
        return getFile(fileId) bindUi { file ->
            if (file == null)
                throw FileNotFoundException()

            val download = Download(
                generateDownloadId(),
                file.id,
                DownloadState.CREATED,
                localFilePath,
                true,
                null
            )

            val info = DownloadInfo(
                download,
                file
            )

            transferManager.download(info)
        }
    }
}