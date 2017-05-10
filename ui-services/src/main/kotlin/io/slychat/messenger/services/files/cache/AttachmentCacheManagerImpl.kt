package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.minusAssign
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.files.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*
import kotlin.collections.HashSet

/**
 * Handles managing cache download requests and updating the underlying AttachmentCache
 */
class AttachmentCacheManagerImpl(
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val storageService: StorageService,
    private val attachmentCache: AttachmentCache,
    private val attachmentCachePersistenceManager: AttachmentCachePersistenceManager,
    private val thumbnailGenerator: ThumbnailGenerator,
    fileEvents: Observable<RemoteFileEvent>,
    messageUpdateEvents: Observable<MessageUpdateEvent>
) : AttachmentCacheManager {
    private data class ThumbnailJob(val fileId: String, val resolution: Int)

    private val log = LoggerFactory.getLogger(javaClass)

    private var subscriptions = CompositeSubscription()

    //fileId
    private val allRequests = HashMap<String, AttachmentCacheRequest>()

    private var currentThumbnailJob: ThumbnailJob? = null

    //thumbnail jobs for pending downloads
    private val pendingThumbnailQueue = ArrayDeque<ThumbnailJob>()

    private val thumbnailingQueue = ArrayDeque<ThumbnailJob>()

    private val fileIdToDownloadId = HashMap<String, String>()

    private val downloadIdToFileId = HashMap<String, String>()

    init {
        subscriptions += fileEvents.ofType(RemoteFileEvent.Deleted::class.java).subscribe {
            //ref_count will already be set to 0
            onFilesDelete(it.files)
        }

        subscriptions += storageService.transferEvents.ofType(TransferEvent.StateChanged::class.java).subscribe { onTransferEvent(it) }

        subscriptions += messageUpdateEvents.subscribe { onMessageUpdateEvent(it) }
    }

    private fun onMessageUpdateEvent(ev: MessageUpdateEvent) {
        when (ev) {
            is MessageUpdateEvent.Deleted -> checkForDeletedFiles()

            is MessageUpdateEvent.DeletedAll -> checkForDeletedFiles()
        }
    }

    private fun deleteCachedFiles(fileIds: List<String>) {
        log.info("Deleting cached files")

        attachmentCache.delete(fileIds) fail {
            log.error("Unable to delete files: {}", it.message, it)
        }
    }

    //user deleted file, so clean up any local copies
    //file deletion will set associated ref counts to zero already, so just delete the files
    private fun onFilesDelete(files: List<RemoteFile>) {
        val fileIds = files.map { it.id }

        log.info("Files deleted, cleaning up cache entries, cancelling transactions")

        cancelTransfers(fileIds)

        attachmentCache.delete(fileIds) fail {
            log.error("Unable to delete files: {}", it.message, it)
        }
    }

    private fun cancelTransfers(fileIds: List<String>) {
        val ids = HashSet(fileIds)

        val toCancel = storageService.transfers
            .filter { it.file?.id in ids }
            .map { it.id }

        storageService.cancel(toCancel)
    }

    private fun checkForDeletedFiles() {
        attachmentCachePersistenceManager.getZeroRefCountFiles() bindUi {
            cancelTransfers(it)
            deleteCachedFiles(it)
            attachmentCachePersistenceManager.deleteZeroRefCountEntries(it)
        } fail {
            log.error("Unable to read files with zero ref counts from storage", it.message, it)
        }
    }

    override fun init() {
        checkForDeletedFiles()

        attachmentCachePersistenceManager.getAllRequests() successUi {
            trackNewRequests(it)
        } fail {
            log.error("Failed to read existing requests from storage", it.message, it)
        }
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun addRequests(requests: List<AttachmentCacheRequest>): Promise<Unit, Exception> {
        //insert before saving, to insure the same request can't be made twice
        trackNewRequests(requests)

        return attachmentCachePersistenceManager.addRequests(requests)
    }

    /** Returns all ids not currently in the queued list. */
    private fun filterExisting(fileIds: List<String>): List<String> {
        return fileIds.filter { it !in allRequests }
    }

    override fun requestCache(fileIds: List<String>): Promise<Unit, Exception> {
        val idSet = HashSet(fileIds)

        return attachmentCache.filterPresent(idSet) bindUi { cachedFileIds ->
            val toCache = idSet.filterNot { it in cachedFileIds }

            val requests = filterExisting(toCache).map {
                AttachmentCacheRequest(it, null)
            }

            addRequests(requests)
        }
    }

    private fun trackNewRequests(requests: List<AttachmentCacheRequest>) {
        if (requests.isEmpty())
            return

        val toDownload = ArrayList<AttachmentCacheRequest>()

        requests.forEach {
            allRequests[it.fileId] = it

            if (it.downloadId == null) {
                toDownload.add(it)
            }
            else {
                trackDownload(it.downloadId!!, it.fileId)
            }
        }

        downloadToCache(toDownload)
    }

    private fun trackDownload(downloadId: String, fileId: String) {
        fileIdToDownloadId[fileId] = downloadId
        downloadIdToFileId[downloadId] = fileId
    }

    private fun untrackDownload(download: Download) {
        downloadIdToFileId -= download.id
        fileIdToDownloadId -= download.fileId
    }

    private fun untrackRequest(fileId: String): Promise<Unit, Exception> {
        allRequests -= fileId

        return attachmentCachePersistenceManager.deleteRequests(listOf(fileId))
    }

    //TODO we actually need to check transfers on startup as well, incase we don't process a transfer event and the app closes
    private fun onTransferEvent(ev: TransferEvent.StateChanged) {
        when (ev.transfer) {
            is Transfer.D -> {
                onDownloadStateChanged(ev.transfer.download, ev)
            }

            is Transfer.U -> {}
        }
    }

    private fun onDownloadStateChanged(download: Download, ev: TransferEvent.StateChanged) {
        val fileId = downloadIdToFileId[download.id] ?: return

        when (ev.state) {
            TransferState.COMPLETE -> {
                log.info("Download for {} complete", fileId)

                untrackDownload(download)
                attachmentCache.markOriginalComplete(listOf(fileId)) bindUi {
                    untrackRequest(fileId)
                } fail {
                    log.error("Unable to untrack request: {}", it.message, it)
                }

                processPendingThumbnailQueue(fileId)
            }

            //I guess do nothing? we should probably raise some kinda event though
            TransferState.ERROR -> {
                TODO()
            }

            //TODO deleted associated request
            TransferState.CANCELLED -> {
                TODO()
            }

            else -> {
                //do nothing
            }
        }
    }

    private fun processPendingThumbnailQueue(fileId: String) {
        val wasRemoved = pendingThumbnailQueue.removeIf {
            val matches = it.fileId == fileId

            if (matches) {
                thumbnailingQueue.add(it)
            }

            matches
        }

        if (wasRemoved)
            nextThumbnailingJob()
    }

    private fun addToThumbnailingQueue(job: ThumbnailJob, isPending: Boolean) {
        if (job == currentThumbnailJob || thumbnailingQueue.contains(job) || pendingThumbnailQueue.contains(job))
            return

        if (!isPending) {
            thumbnailingQueue.add(job)
            nextThumbnailingJob()
        }
        else
            pendingThumbnailQueue.add(job)
    }

    private fun nextThumbnailingJob() {
        if (currentThumbnailJob != null || thumbnailingQueue.isEmpty())
            return

        val job = thumbnailingQueue.pop()
        currentThumbnailJob = job

        fileListPersistenceManager.getFile(job.fileId) bind {
            if (it == null || it.isDeleted)
                throw InvalidFileException(job.fileId)

            val streams = attachmentCache.getThumbnailGenerationStreams(
                job.fileId,
                job.resolution,
                it.userMetadata.fileKey,
                CipherList.getCipher(it.userMetadata.cipherId),
                it.fileMetadata!!.chunkSize
            )

            streams?.use {
                thumbnailGenerator.generateThumbnail(it.inputStream, it.outputStream, job.resolution) map { true }
            } ?: Promise.of(false)
        } bindUi { wasOriginalPresent ->
            if (wasOriginalPresent) {
                log.info("Thumbnail generated for {}@{}", job.fileId, job.resolution)
                attachmentCache.markThumbnailComplete(job.fileId, job.resolution)
            }
            else {
                log.info("Original file missing, requesting download")
                requestOriginalAndThumbnail(job.fileId, job.resolution, false)
                Promise.of(Unit)
            }
        } successUi {
            currentThumbnailJob = null
            nextThumbnailingJob()
        } failUi {
            if (it is InvalidFileException) {
                log.warn("Failed to generate thumbnail for {}, file has disappeared", job.fileId)
            }
            else
                log.error("Failed to generate thumbnail for {}: {}", job, it.message, it)

            currentThumbnailJob = null
            nextThumbnailingJob()
        }
    }

    //this will be called off the main thread
    override fun getImageStream(fileId: String): Promise<ImageLookUpResult, Exception> {
        return fileListPersistenceManager.getFile(fileId) map {
            //null if deleted via sync, isDeleted if just deleted locally
            if (it == null || it.isDeleted)
                ImageLookUpResult(null, true, false)
            else {
                val fileMetadata = it.fileMetadata!!

                val stream = attachmentCache.getOriginalImageInputStream(
                    fileId,
                    it.userMetadata.fileKey,
                    CipherList.getCipher(it.userMetadata.cipherId),
                    fileMetadata.chunkSize
                )

                ImageLookUpResult(stream, false, true)
            }
        } successUi {
            if (it.inputStream == null && !it.isDeleted) {
                if (fileId !in allRequests) {
                    addRequests(listOf(AttachmentCacheRequest(fileId, null))) fail {
                        log.error("Failed to add download request for original image to queue: {}", it.message, it)
                    }

                    log.info("{} requested, creating download request", fileId)
                }
            }
        }
    }

    private fun requestOriginalAndThumbnail(fileId: String, resolution: Int, isOriginalPresent: Boolean) {
        if (!isOriginalPresent && fileId !in allRequests) {
            addRequests(listOf(AttachmentCacheRequest(fileId, null))) fail {
                log.error("Failed to add download request for original image to queue: {}", it.message, it)
            }
        }

        addToThumbnailingQueue(ThumbnailJob(fileId, resolution), !isOriginalPresent)
    }

    override fun getThumbnailStream(fileId: String, resolution: Int): Promise<ImageLookUpResult, Exception> {
        return fileListPersistenceManager.getFile(fileId) map {
            if (it == null || it.isDeleted)
                ImageLookUpResult(null, true, false)
            else {
                val fileMetadata = it.fileMetadata!!

                val stream = attachmentCache.getThumbnailInputStream(
                    fileId,
                    resolution,
                    it.userMetadata.fileKey,
                    CipherList.getCipher(it.userMetadata.cipherId),
                    fileMetadata.chunkSize
                )

                ImageLookUpResult(stream, false, attachmentCache.isOriginalPresent(fileId))
            }
        } successUi {
            if (it.inputStream == null && !it.isDeleted) {
                requestOriginalAndThumbnail(fileId, resolution, it.isOriginalPresent)
            }
        }
    }

    private fun downloadToCache(requests: List<AttachmentCacheRequest>) {
        if (requests.isEmpty())
            return

        val tracking = HashMap<String, AttachmentCacheRequest>()

        val dlRequests = requests.map {
            tracking[it.fileId] = it
            DownloadRequest(it.fileId, attachmentCache.getPendingPathForFile(it.fileId).path)
        }

        storageService.downloadFiles(dlRequests) bindUi {
            val toUpdate = it.map {
                val fileId = it.download.fileId
                val request = tracking[fileId]!!
                val downloadId = it.download.id

                trackDownload(downloadId, fileId)

                val updated = request.copy(downloadId = downloadId)

                allRequests[request.fileId] = updated

                updated
            }

            attachmentCachePersistenceManager.updateRequests(toUpdate)
        } fail {
            log.error("Unable to queue files for download: {}", it.message, it)
        }
    }
}