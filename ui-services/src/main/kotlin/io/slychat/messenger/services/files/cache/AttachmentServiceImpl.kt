package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.files.encryptUserMetadata
import io.slychat.messenger.core.files.getFilePathHash
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.AcceptShareResponse
import io.slychat.messenger.core.http.api.share.ShareAsyncClient
import io.slychat.messenger.core.http.api.share.ShareInfo
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ReceivedAttachment
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.messaging.MessageService
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*

//the ui doesn't care if the attachment is received or just sent; for sent, we just need to generate thumbnails
sealed class AttachmentEvent {
    class StateChanged(val state: ReceivedAttachmentState) : AttachmentEvent()

    //UntilRetry?
}

enum class ReceivedAttachmentState {
    PENDING,
    ACCEPTED,
    WAITING_ON_SYNC,
    COMPLETE,
    //associated transfer was cancelled
    CANCELLED,
    //file was deleted by sender; this is terminal
    MISSING
}

//TODO update quota somehow; maybe quota should be moved to a separate component since it's updated in a few spots?
//TODO need something to fetch and retry attachments that failed
//TODO need a watch to know to start thumbnail jobs
//TODO htf do we notify the ui of non-transient errors/state updates?
//TODO we actually need to make sure to wait until after sync to notify that something is available
class AttachmentServiceImpl(
    private val keyVault: KeyVault,
    private val tokenManager: AuthTokenManager,
    private val shareClient: ShareAsyncClient,
    private val messageService: MessageService,
    private val storageService: StorageService,
    messageUpdateEvents: Observable<MessageUpdateEvent>,
    networkStatus: Observable<Boolean>,
    syncEvents: Observable<FileListSyncEvent>
) : AttachmentService {
    private class AttachmentInfo(val conversationId: ConversationId, val messageId: String, val attachment: ReceivedAttachment)
    private class AcceptJob(val conversationId: ConversationId, val sender: UserId, val messageId: String, val attachments: List<ReceivedAttachment>)

    private val log = LoggerFactory.getLogger(javaClass)

    //updateReceivedAttachmentState(conversationId, messageId,

    private var current: AcceptJob? = null

    //waiting to be restored
    private val transientErrorQueue = ArrayList<Unit>()

    //waiting for file sync
    //TODO we should check if we already have
    private val waitingForSync = HashMap<String, AttachmentInfo>()

    //XXX we should just have a queue for accepting, and let the other components handle their own queue?
    //we need a list of attachments waiting to show up in sync; then these need to be sent to the downloader (which has its own queue) (XXX we need to track the downloadId here if inline)
    //then we need to watch those for completion/etc, then send those to the thumbnailer (which should have its own queue I guess)
    //then we're done
    private val queue = ArrayDeque<AcceptJob>()

    private val subscriptions = CompositeSubscription()

    private var isNetworkAvailable = false

    init {
        subscriptions += networkStatus.subscribe { onNetworkAvailability(it) }
        subscriptions += messageUpdateEvents.subscribe { onMessageUpdateEvent(it) }
        subscriptions += syncEvents.ofType(FileListSyncEvent.Result::class.java).subscribe { onFileListSyncResult(it) }
    }

    private fun onFileListSyncResult(ev: FileListSyncEvent.Result) {
        val present = ev.result.mergeResults.added.filter { it.id in waitingForSync }
        if (present.isEmpty())
            return

        val completed = HashMap<Pair<ConversationId, String>, MutableList<Int>>()

        present.map { waitingForSync[it.id]!! }.forEach {
            if (it.attachment.isInline) {
                //TODO we need get downloadFile to return the associated Download
                //TODO this should be deferred to the AttachmentCacheManager? we need the path
                //TODO we also need to update the received attachment with the downloadid
                //storageService.downloadFile(it.attachment.fileId, "") successUi {} failUi {}
                TODO()
            }
            else {
                //complete
                val l = completed.getOrPut(it.conversationId to it.messageId) { ArrayList() }
                l.add(it.attachment.n)
            }
        }

        completed.forEach {
            val (conversationId, messageId) = it.key
            messageService.deleteReceivedAttachments(conversationId, messageId, it.value) fail {
                log.error("Failed to remove received attachments: {}", it.message, it)
            }
        }
    }

    private fun onMessageUpdateEvent(ev: MessageUpdateEvent) {
        when (ev) {
            is MessageUpdateEvent.Deleted -> TODO()
            is MessageUpdateEvent.DeletedAll -> TODO()
        }
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
                ShareInfo(it.fileId, it.theirShareKey, generateShareKey(), um, pathHash)
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
        val all = acceptJob.attachments.mapTo(HashSet()) { it.fileId }

        //TODO wtf do we do with this
        val errors = response.errors.keys
        if (errors.isNotEmpty()) {
            TODO()
        }

        //TODO an issue here is that if a sync happens before we process the result here (eg: triggered by something else), we could miss some attachments
        val successful = all - errors

        acceptJob.attachments.filter { it.fileId in successful }.forEach {
            waitingForSync[it.fileId] = AttachmentInfo(acceptJob.conversationId, acceptJob.messageId, it)
        }

        storageService.sync()
    }

    override fun init() {
        //TODO need to fetch all pending attachments and queue them for download?
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    override fun addNewReceived(conversationId: ConversationId, sender: UserId, messageId: String, receivedAttachments: List<ReceivedAttachment>) {
        queue.add(AcceptJob(conversationId, sender, messageId, receivedAttachments))

        processNext()
    }
}