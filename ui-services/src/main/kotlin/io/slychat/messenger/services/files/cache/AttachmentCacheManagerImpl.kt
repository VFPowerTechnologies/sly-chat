package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.minusAssign
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.files.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.io.InputStream
import java.util.*

//TODO track message deletion
/**
 * Handles managing cache download requests and updating the underlying AttachmentCache
 */
class AttachmentCacheManagerImpl(
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val storageService: StorageService,
    private val attachmentCache: AttachmentCache,
    private val attachmentCachePersistenceManager: AttachmentCachePersistenceManager,
    private val thumbnailGenerator: ThumbnailGenerator,
    fileEvents: Observable<RemoteFileEvent>
) : AttachmentCacheManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private var subscriptions = CompositeSubscription()

    //fileId
    private val allRequests = HashMap<String, AttachmentCacheRequest>()

    //fileId
    private val thumbnailingQueue = ArrayDeque<String>()

    private val fileIdToDownloadId = HashMap<String, String>()

    private val downloadIdToFileId = HashMap<String, String>()

    init {
        subscriptions += fileEvents.ofType(RemoteFileEvent.Deleted::class.java).subscribe {
            //ref_count will already be set to 0
            onFilesDelete(it.files)
        }

        subscriptions += storageService.transferEvents.ofType(TransferEvent.StateChanged::class.java).subscribe { onTransferEvent(it) }
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

        log.info("Files deleted, cleaning up cache entries")

        attachmentCache.delete(fileIds) fail {
            log.error("Unable to delete files: {}", it.message, it)
        }
    }

    override fun init() {
        attachmentCachePersistenceManager.getZeroRefCountFiles() successUi {
            deleteCachedFiles(it)
        } fail {
            log.error("Unable to read files with zero ref counts from storage", it.message, it)
        }

        attachmentCachePersistenceManager.getAllRequests() successUi {
            trackNewRequests(it)
        } fail {
            log.error("Failed to read existing requests from storage", it.message, it)
        }
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun addRequests(requests: List<AttachmentCacheRequest>, incCountFor: List<String>): Promise<Unit, Exception> {
        return attachmentCachePersistenceManager.addRequests(requests, incCountFor) successUi {
            trackNewRequests(requests)
        }
    }

    override fun requestCache(receivedAttachments: List<ReceivedAttachment>): Promise<Unit, Exception> {
        val fileIds = receivedAttachments.mapToSet { it.fileId }

        //TODO maybe just check downloads here, instead of having filterPresent do it?
        return attachmentCache.filterPresent(fileIds) bindUi { cachedFileIds ->
            val (alreadyCached, toCache) = receivedAttachments.partition { it.fileId in cachedFileIds }

            val requests = toCache.map {
                AttachmentCacheRequest(it.fileId, null, AttachmentCacheRequest.State.PENDING)
            }

            //TODO alreadyCached event
            addRequests(requests, alreadyCached.map { it.fileId })
        }
    }

    private fun trackNewRequests(requests: List<AttachmentCacheRequest>) {
        if (requests.isEmpty())
            return

        val toDownload = ArrayList<AttachmentCacheRequest>()

        requests.forEach {
            allRequests[it.fileId] = it

            when (it.state) {
                AttachmentCacheRequest.State.PENDING -> {
                    toDownload.add(it)
                }

                AttachmentCacheRequest.State.DOWNLOADING -> {
                    trackDownload(it.downloadId!!, it.fileId)
                }

                AttachmentCacheRequest.State.THUMBNAILING -> {
                    addToThumbnailingQueue(it.fileId)
                }
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

    private fun untrackRequest(fileId: String, downloadId: String) {
        allRequests -= fileId

        attachmentCachePersistenceManager.deleteRequests(listOf(fileId)) fail {
            log.error("Failed to delete requests: {}", it.message, it)
        }
    }

    //TODO we actually need to check transfers on startup as well, incase we don't process a transfer event and the app closes
    private fun onTransferEvent(ev: TransferEvent.StateChanged) {
        when (ev.transfer) {
            is Transfer.D -> {
                onDownload(ev.transfer.download, ev)
            }

            is Transfer.U -> {}
        }
    }

    private fun onDownload(download: Download, ev: TransferEvent.StateChanged) {
        val fileId = downloadIdToFileId[download.id] ?: return

        when (ev.state) {
            TransferState.COMPLETE -> {
                log.info("Download for {} complete", fileId)

                untrackDownload(download)

                val updated = allRequests[fileId]!!.copy(state = AttachmentCacheRequest.State.THUMBNAILING)

                attachmentCachePersistenceManager.updateRequests(listOf(updated)) successUi {
                    addToThumbnailingQueue(fileId)
                } fail {
                    log.error("Failed to update request to thumbnailing state: {}", it.message, it)
                }
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

    private fun addToThumbnailingQueue(fileId: String) {
        thumbnailingQueue.add(fileId)
        nextThumbnailingJob()
    }

    private fun nextThumbnailingJob() {
        if (thumbnailingQueue.isEmpty())
            return

        val fileId = thumbnailingQueue.pop()

        thumbnailGenerator.generateThumbnails(fileId) bindUi {
            log.info("Thumbnails generated for {}", fileId)

            allRequests -= fileId

            attachmentCachePersistenceManager.deleteRequests(listOf(fileId)) successUi {
                nextThumbnailingJob()
            }
        } failUi {
            log.error("Failed to generate thumbnails for {}: {}", fileId, it.message, it)
            nextThumbnailingJob()
        }
    }

    //this will be called off the main thread
    override fun getImageStream(fileId: String): Promise<InputStream?, Exception> {
        return fileListPersistenceManager.getFile(fileId) map {
            //null if deleted via sync, isDeleted if just deleted locally
            if (it == null || it.isDeleted)
                null
            else {
                val fileMetadata = it.fileMetadata!!

                attachmentCache.getImageStream(
                    fileId,
                    it.userMetadata.fileKey,
                    CipherList.getCipher(it.userMetadata.cipherId),
                    fileMetadata.chunkSize
                )
            }
        } successUi {
            if (it == null && fileId !in allRequests) {
                addRequests(listOf(AttachmentCacheRequest(fileId, null, AttachmentCacheRequest.State.PENDING)), emptyList())
            }
        }
    }

    private fun downloadToCache(requests: List<AttachmentCacheRequest>) {
        if (requests.isEmpty())
            return

        val tracking = HashMap<String, AttachmentCacheRequest>()

        val dlRequests = requests.map {
            tracking[it.fileId] = it
            DownloadRequest(it.fileId, attachmentCache.getDownloadPathForFile(it.fileId).path)
        }

        storageService.downloadFiles(dlRequests) bindUi {
            val toUpdate = it.map {
                val fileId = it.download.fileId
                val request = tracking[fileId]!!
                val downloadId = it.download.id

                trackDownload(downloadId, fileId)

                val updated = request.copy(downloadId = downloadId, state = AttachmentCacheRequest.State.DOWNLOADING)

                allRequests[request.fileId] = updated

                updated
            }

            attachmentCachePersistenceManager.updateRequests(toUpdate)
        } fail {
            log.error("Unable to queue files for download: {}", it.message, it)
        }
    }
}