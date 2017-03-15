package io.slychat.messenger.services.files

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.UserPaths
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.io.FileNotFoundException

//XXX an issue right now is that upload adds a file to the list but it isn't reflected in the file list until the upload completes
//I guess this isn't that much of an issue anyways
class StorageServiceImpl(
    private val authTokenManager: AuthTokenManager,
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val syncJobFactory: StorageSyncJobFactory,
    private val transferManager: TransferManager,
    private val fileAccess: PlatformFileAccess,
    private val userPaths: UserPaths,
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
    //if another sync is triggered during the current one (eg: upload completed while we were syncing, so we need to
    //sync again)
    private var isSyncPending = false

    override val transferEvents: Observable<TransferEvent>
        get() = transferManager.events

    private var subscriptions = CompositeSubscription()

    private var isNetworkAvailable = false

    override val uploads: List<UploadStatus>
        get() = transferManager.uploads

    override val downloads: List<DownloadStatus>
        get() = transferManager.downloads

    init {
        subscriptions += networkStatus.subscribe { onNetworkStatusChange(it) }
        subscriptions += transferEvents.subscribe { onTransferEvent(it) }
    }

    private fun onTransferEvent(event: TransferEvent) {
        if (event !is TransferEvent.UploadStateChanged)
            return

        if (event.state == TransferState.COMPLETE)
            sync()
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isNetworkAvailable)
            nextSync()
    }

    private fun nextSync() {
        if (!isSyncPending)
            return

        isSyncPending = false
        sync()
    }

    private fun beginSync() {
        isSyncRunning = true
        syncEventsSubject.onNext(FileListSyncEvent.Begin())
    }

    private fun endSync(hasError: Boolean) {
        isSyncRunning = false

        syncEventsSubject.onNext(FileListSyncEvent.End(hasError))

        nextSync()
    }

    private fun onSyncError(t: Throwable) {
        //TODO track errors
        endSync(true)
    }

    private fun onSyncSuccess(result: StorageSyncResult) {
        quotaSubject.onNext(result.quota)
        syncEventsSubject.onNext(FileListSyncEvent.Result(result))

        endSync(false)
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    override fun sync() {
        if (isSyncRunning || !isNetworkAvailable) {
            isSyncPending = true
            return
        }

        beginSync()

        authTokenManager.bind {
            syncJobFactory.create(it).run()
        } successUi {
            log.info("Sync job complete: remoteUpdatesPerformed={}; newListVersion={}", it.remoteUpdatesPerformed, it.newListVersion)
            onSyncSuccess(it)
        } fail {
            log.condError(isNotNetworkError(it), "Sync job failed: {}", it.message, it)
            onSyncError(it)
        }
    }

    override fun clearSyncError() {
        if (isSyncRunning)
            error("clearSyncError called while sync was running")

        syncEventsSubject.onNext(FileListSyncEvent.End(false))
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

    override fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception> {
        return task {
            val fileInfo = fileAccess.getFileInfo(localFilePath)
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

            val parts = if (!cache)
                calcUploadParts(cipher, fileInfo.size, chunkSize, MIN_PART_SIZE)
            else
                calcUploadPartsEncrypted(remoteFileSize, MIN_PART_SIZE)

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

            val cachePath = if (cache)
                (userPaths.fileCacheDir / file.id).toString()
            else
                null

            val upload = Upload(
                generateUploadId(),
                file.id,
                UploadState.PENDING,
                fileInfo.displayName,
                localFilePath,
                cachePath,
                cache == true,
                null,
                parts
            )

            UploadInfo(
                upload,
                file
            )
        } bindUi {
            transferManager.upload(it)
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

    override fun removeUploads(uploadIds: List<String>): Promise<Unit, Exception> {
        return transferManager.removeUploads(uploadIds)
    }

    override fun removeDownloads(downloadIds: List<String>): Promise<Unit, Exception> {
        return transferManager.removeDownloads(downloadIds)
    }

    override fun retryDownload(downloadId: String): Promise<Unit, Exception> {
        return transferManager.clearDownloadError(downloadId)
    }

    override fun retryUpload(uploadId: String): Promise<Unit, Exception> {
        return transferManager.clearUploadError(uploadId)
    }

    override fun cancelDownloads(downloadIds: List<String>) {
        downloadIds.forEach {
            transferManager.cancelDownload(it)
        }
    }

    override fun removeCompletedDownloads(): Promise<Unit, Exception> {
        return transferManager.removeCompletedDownloads()
    }

    override fun removeCompletedUploads(): Promise<Unit, Exception> {
        return transferManager.removeCompletedUploads()
    }
}