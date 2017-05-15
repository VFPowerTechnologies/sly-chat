package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.encryptUserMetadata
import io.slychat.messenger.core.files.getFilePathHash
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.AcceptShareResponse
import io.slychat.messenger.core.http.api.share.ShareAsyncClient
import io.slychat.messenger.core.http.api.share.ShareInfo
import io.slychat.messenger.core.persistence.AttachmentId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ReceivedAttachment
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.messaging.MessageService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*

//TODO update quota somehow; maybe quota should be moved to a separate component since it's updated in a few spots?
//TODO need something to fetch and retry attachments that failed
//TODO htf do we notify the ui of non-transient errors/state updates?
//TODO we actually need to make sure to wait until after sync to notify that something is available
/**
 * This handles auto-accepting shared attachments from messages.
 *
 * If the attachments should be inlined (eg: images), then these are passed off to AttachmentCacheManager.
 */
class AttachmentServiceImpl(
    private val keyVault: KeyVault,
    private val tokenManager: AuthTokenManager,
    private val shareClient: ShareAsyncClient,
    private val messageService: MessageService,
    private val storageService: StorageService,
    private val attachmentCacheManager: AttachmentCacheManager,
    networkStatus: Observable<Boolean>,
    syncEvents: Observable<FileListSyncEvent>
) : AttachmentService {
    companion object {
        internal val INLINE_FILE_SIZE_LIMIT: Long = 6L.mb
    }

    override val events: Observable<AttachmentCacheEvent>
        get() = attachmentCacheManager.events

    private class AcceptJob(val sender: UserId, val attachments: List<ReceivedAttachment>)

    private val attachments = ReceivedAttachments()

    private val log = LoggerFactory.getLogger(javaClass)

    private var current: AcceptJob? = null

    //waiting to be restored
    private val transientErrorQueue = ArrayList<Unit>()

    //XXX we should just have a queue for accepting, and let the other components handle their own queue?
    //we need a list of attachments waiting to show up in sync; then these need to be sent to the downloader (which has its own queue) (XXX we need to track the downloadId here if inline)
    //then we need to watch those for completion/etc, then send those to the thumbnailer (which should have its own queue I guess)
    //then we're done
    private val queue = ArrayDeque<AcceptJob>()

    private val subscriptions = CompositeSubscription()

    private var isNetworkAvailable = false

    init {
        subscriptions += networkStatus.subscribe { onNetworkAvailability(it) }
        subscriptions += syncEvents.ofType(FileListSyncEvent.Result::class.java).subscribe { onFileListSyncResult(it) }
    }

    private fun onFileListSyncResult(ev: FileListSyncEvent.Result) {
        val completed = ArrayList<AttachmentId>()
        val markInline = ArrayList<AttachmentId>()

        for (file in ev.result.mergeResults.added) {
            val attachment = attachments.getWaitingForSync(file.id) ?: continue

            if (shouldInline(file))
                markInline.add(attachment.id)

            completed.add(attachment.id)
        }

        completed.forEach {
            val toCache = markInline.map { attachments.get(it)!! }
            attachments.toComplete(completed)

            messageService.deleteReceivedAttachments(completed, markInline) bindUi {
                attachmentCacheManager.requestCache(toCache.map { it.ourFileId })
            } fail {
                log.error("Failed to remove received attachments: {}", it.message, it)
            }
        }
    }

    private fun shouldInline(file: RemoteFile): Boolean {
        val fileMetadata = file.fileMetadata ?: return false

        return fileMetadata.mimeType.startsWith("image/") && fileMetadata.size <= INLINE_FILE_SIZE_LIMIT
    }

    private fun onNetworkAvailability(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isAvailable)
            processNext()
    }

    private fun processNext() {
        if (!isNetworkAvailable || current != null)
            return

        if (queue.isEmpty()) {
            log.info("Queue empty")
            return
        }

        val job = queue.pop()
        current = job

        tokenManager.bindUi {
            val shareInfo = job.attachments.map {
                val um = encryptUserMetadata(keyVault, it.userMetadata)
                val pathHash = getFilePathHash(keyVault, it.userMetadata)
                ShareInfo(it.theirFileId, it.ourFileId, it.theirShareKey, generateShareKey(), um, pathHash)
            }

            val request = AcceptShareRequest(
                job.sender,
                shareInfo
            )

            shareClient.acceptShare(it, request)
        } successUi {
            completeAcceptJob(job, it)
            nextJob()
        } failUi {
            log.condError(isNotNetworkError(it), "Failed to accept attachments: {}", it.message, it)
            //TODO handle this somehow based on error
            nextJob()
        }
    }

    private fun nextJob() {
        current = null
        processNext()
    }

    private fun completeAcceptJob(acceptJob: AcceptJob, response: AcceptShareResponse) {
        val all = acceptJob.attachments.mapTo(HashSet()) { it.theirFileId }

        //TODO wtf do we do with this
        val errors = response.errors.keys
        if (errors.isNotEmpty()) {
            TODO()
        }

        //TODO an issue here is that if a sync happens before we process the result here (eg: triggered by something else), we could miss some attachments
        val successful = all - errors

        val successfulIds = acceptJob.attachments
            .filterMap {
                if (it.theirFileId in successful)
                    it.id
                else
                    null
            }

        attachments.toWaitingOnSync(successfulIds)

        storageService.sync()
    }

    override fun init() {
        attachmentCacheManager.init()

        messageService.getAllReceivedAttachments() successUi {
            attachments.add(it)

            attachments.getPending().forEach {
                val sender = it.userMetadata.sharedFrom!!.userId
                //TODO grouping
                queue.add(AcceptJob(sender, listOf(it)))
            }

            nextJob()
        } fail {
            log.error("Failed to fetch received attachments: {}", it.message, it)
        }
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    override fun addNewReceived(conversationId: ConversationId, sender: UserId, receivedAttachments: List<ReceivedAttachment>) {
        if (receivedAttachments.isEmpty())
            return

        attachments.add(receivedAttachments)
        queue.add(AcceptJob(sender, receivedAttachments))

        processNext()
    }

    override fun getImageStream(fileId: String): Promise<ImageLookUpResult, Exception> {
        return attachmentCacheManager.getImageStream(fileId)
    }

    override fun getThumbnailStream(fileId: String, resolution: Int): Promise<ImageLookUpResult, Exception> {
        return attachmentCacheManager.getThumbnailStream(fileId, resolution)
    }

    override fun requestCache(fileIds: List<String>): Promise<Unit, Exception> {
        return attachmentCacheManager.requestCache(fileIds)
    }
}