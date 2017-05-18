package io.slychat.messenger.services.files

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.files.cache.AttachmentCache
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

class StorageServiceImpl(
    private val authTokenManager: AuthTokenManager,
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val syncJobFactory: StorageSyncJobFactory,
    private val transferManager: TransferManager,
    private val fileAccess: PlatformFileAccess,
    private val attachmentCache: AttachmentCache,
    private val quotaManager: QuotaManager,
    networkStatus: Observable<Boolean>
) : StorageService {
    private val log = LoggerFactory.getLogger(javaClass)

    override val quota: Observable<Quota>
        get() = quotaManager.quota

    private val syncEventsSubject = BehaviorSubject.create<FileListSyncEvent>()

    override val syncEvents: Observable<FileListSyncEvent>
        get() = syncEventsSubject

    private var isSyncRunning = false
    //if another sync is triggered during the current one (eg: upload completed while we were syncing, so we need to
    //sync again)
    private var isSyncPending = false

    override val transferEvents: Observable<TransferEvent>
        get() = transferManager.events

    private val fileEventsSubject = PublishSubject.create<RemoteFileEvent>()

    override val fileEvents: Observable<RemoteFileEvent>
        get() = fileEventsSubject

    private var subscriptions = CompositeSubscription()

    private var isNetworkAvailable = false

    override val transfers: List<TransferStatus>
        get() = transferManager.transfers

    init {
        subscriptions += networkStatus.subscribe { onNetworkStatusChange(it) }
        subscriptions += transferEvents.ofType(TransferEvent.StateChanged::class.java).subscribe { onTransferEvent(it) }
    }

    private fun onTransferEvent(event: TransferEvent.StateChanged) {
        if (!event.transfer.isUpload)
            return

        when (event.state) {
            TransferState.COMPLETE, TransferState.CANCELLED -> sync()
            else -> {}
        }
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

    private fun onSyncSuccess(result: FileListSyncResult) {
        quotaManager.update(result.quota)
        syncEventsSubject.onNext(FileListSyncEvent.Result(result))

        val mergeResults = result.mergeResults

        if (mergeResults.added.isNotEmpty())
            fileEventsSubject.onNext(RemoteFileEvent.Added(mergeResults.added))

        if (mergeResults.deleted.isNotEmpty())
            fileEventsSubject.onNext(RemoteFileEvent.Deleted(mergeResults.deleted))

        if (mergeResults.updated.isNotEmpty())
            fileEventsSubject.onNext(RemoteFileEvent.Updated(mergeResults.updated))

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
        return fileListPersistenceManager.getFiles(startingAt, count, false, false)
    }

    override fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception> {
        return fileListPersistenceManager.getFilesAt(startingAt, count, false, false, path)
    }

    override fun getFilesById(fileIds: List<String>): Promise<Map<String, RemoteFile>, Exception> {
        return fileListPersistenceManager.getFilesById(fileIds)
    }

    override fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<DirEntry>, Exception> {
        return fileListPersistenceManager.getEntriesAt(startingAt, count, false, path)
    }

    //TODO move this into some method on TransferManager?
    override fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception> {
        val s = HashSet(fileIds)
        val transfers = transferManager.transfers
            .filter { it.file != null && it.file.id in s }
            .map { it.id }

        return transferManager.remove(transfers) bind {
            fileListPersistenceManager.deleteFiles(fileIds) mapUi {
                fileEventsSubject.onNext(RemoteFileEvent.Deleted(it))
                sync()
                Unit
            }
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
                remoteFileName, null
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
                attachmentCache.getFinalPathForFile(file.id).path
            else
                null

            val remoteFilePath = if (remoteFileDirectory != "/")
                "$remoteFileDirectory/$remoteFileName"
            else
                "$remoteFileDirectory$remoteFileName"

            val upload = Upload(
                generateUploadId(),
                file.id,
                UploadState.PENDING,
                fileInfo.displayName,
                remoteFilePath,
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
        } bindUi { info ->
            transferManager.upload(info)
        }
    }

    override fun downloadFiles(requests: List<DownloadRequest>): Promise<List<DownloadInfo>, Exception> {
        return getFilesById(requests.map { it.fileId }) bindUi { files ->
            val downloads = requests.map {
                val file = files[it.fileId]!!

                val download = Download(
                    generateDownloadId(),
                    it.fileId,
                    DownloadState.CREATED,
                    it.localFilePath,
                    "${file.userMetadata.directory}/${file.userMetadata.fileName}",
                    it.doDecrypt,
                    null
                )

                DownloadInfo(
                    download,
                    file
                )
            }

            transferManager.download(downloads) map { downloads }
        }
    }

    override fun remove(transferIds: List<String>): Promise<Unit, Exception> {
        return transferManager.remove(transferIds)
    }

    override fun retry(transferId: String): Promise<Unit, Exception> {
        return transferManager.clearError(transferId)
    }

    override fun removeCompleted(): Promise<Unit, Exception> {
        return transferManager.removeCompleted()
    }

    override fun cancel(transferIds: List<String>) {
        transferManager.cancel(transferIds)
    }
}