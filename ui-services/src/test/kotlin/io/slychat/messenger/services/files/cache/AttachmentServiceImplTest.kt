package io.slychat.messenger.services.files.cache

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.AcceptShareResponse
import io.slychat.messenger.core.http.api.share.ShareAsyncClient
import io.slychat.messenger.core.persistence.FileListMergeResults
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.FileListSyncResult
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import nl.komponents.kovenant.deferred
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.verification.VerificationMode
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import kotlin.test.assertEquals

class AttachmentServiceImplTest {
    companion object {
        val keyVault = generateNewKeyVault("test")

        @ClassRule
        @JvmField
        val kovenantTestModeRule = KovenantTestModeRule()
    }

    private val tokenManager = MockAuthTokenManager()

    private val shareClient: ShareAsyncClient = mock()

    private val messageService: MessageService = mock()

    private val storageService: StorageService = mock()

    private val networkStatus = BehaviorSubject.create<Boolean>()

    private val messageUpdateEvents = PublishSubject.create<MessageUpdateEvent>()

    private val syncEvents = PublishSubject.create<FileListSyncEvent>()

    @Before
    fun before() {
        whenever(messageService.messageUpdates).thenReturn(messageUpdateEvents)
        whenever(messageService.deleteReceivedAttachments(any(), any(), any())).thenResolveUnit()

        whenever(storageService.syncEvents).thenReturn(syncEvents)

        whenever(shareClient.acceptShare(any(), any())).thenResolve(AcceptShareResponse(emptyMap(), randomQuota()))
    }

    private fun newService(isNetworkAvailable: Boolean = true): AttachmentServiceImpl {
        networkStatus.onNext(isNetworkAvailable)

        return AttachmentServiceImpl(
            keyVault,
            tokenManager,
            shareClient,
            messageService,
            storageService,
            messageUpdateEvents,
            networkStatus,
            syncEvents
        )
    }

    @Test
    fun `it should fetch pending attachments on startup`() {
        TODO()
    }

    @Test
    fun `it should attempt to accept shares when network is available and addNewReceived is called`() {
        val service = newService()

        val conversationId = randomUserConversationId()
        val sender = randomUserId()
        val messageId = randomMessageId()
        val receivedAttachment = randomReceivedAttachment(0)

        service.addNewReceived(conversationId, sender, messageId, listOf(receivedAttachment))

        verify(shareClient).acceptShare(any(), capture {
            assertEquals(it.theirUserId, sender, "Invalid sender")
            assertEquals(1, it.shareInfo.size, "Invalid number of ShareInfo")
            val s = it.shareInfo.first()
            assertEquals(receivedAttachment.fileId, s.fileId, "Invalid fileId")
            assertEquals(receivedAttachment.theirShareKey, s.theirShareKey, "Invalid share key")
        })
    }

    private fun testSyncResponse(fileId: String, mergeResults: FileListMergeResults, times: VerificationMode) {
        val service = newService()

        val conversationId = randomUserConversationId()
        val sender = randomUserId()
        val messageId = randomMessageId()
        val receivedAttachment = randomReceivedAttachment(0, fileId = fileId)

        service.addNewReceived(conversationId, sender, messageId, listOf(receivedAttachment))

        syncEvents.onNext(FileListSyncEvent.Result(FileListSyncResult(0, mergeResults, 1, randomQuota())))

        verify(messageService, times).deleteReceivedAttachments(conversationId, messageId, listOf(receivedAttachment.n))
    }

    @Test
    fun `it should complete a non-inline received attachment when sync succeeds`() {
        val fileId = generateFileId()

        val mergeResults = FileListMergeResults(
            listOf(randomRemoteFile().copy(id = fileId)),
            emptyList(),
            emptyList()
        )

        testSyncResponse(fileId, mergeResults, times(1))
    }

    @Test
    fun `it should not complete a non-inline accepted attachment if it doesn't show up in a sync result`() {
        val fileId = generateFileId()

        val mergeResults = FileListMergeResults(
            listOf(randomRemoteFile()),
            emptyList(),
            emptyList()
        )

        testSyncResponse(fileId, mergeResults, never())
    }

    //XXX in this case, we need to recreate a job with the remaining attachments depending on error?
    @Test
    fun `it should handle a subset of accepted attachments failing`() {
        TODO()
    }

    @Test
    fun `it should move to the next attachment once an attachment is complete`() {
        val service = newService()

        val conversationId = randomUserConversationId()
        val sender = randomUserId()
        val receivedAttachment = randomReceivedAttachment(0)
        val receivedAttachment2 = randomReceivedAttachment(0)

        val d = deferred<AcceptShareResponse, Exception>()
        val response = AcceptShareResponse(emptyMap(), randomQuota())

        whenever(shareClient.acceptShare(any(), any()))
            .thenReturn(d.promise)

        service.addNewReceived(conversationId, sender, randomMessageId(), listOf(receivedAttachment))

        d.resolve(response)

        service.addNewReceived(conversationId, sender, randomMessageId(), listOf(receivedAttachment2))

        val captor = argumentCaptor<AcceptShareRequest>()

        verify(shareClient, times(2)).acceptShare(any(), capture(captor))

        val request = captor.allValues[1]

        assertEquals(1, request.shareInfo.size, "Invalid ShareInfo size")
        val s = request.shareInfo.first()

        assertEquals(receivedAttachment2.fileId, s.fileId, "Invalid fileId")
    }

    @Test
    fun `it should start queued attachments when network becomes available`() {
        TODO()
    }

    @Test
    fun `it should remove attachments from queue that correspond to deleted messages`() {
        TODO()
    }

    @Test
    fun `it should remove attachments from queue that correspond to deleted conversations`() {
        TODO()
    }

    //TODO wtf do we do if we're in the middle of accepting an attachment and a file is deleted? we need to delete it after acceptance
}