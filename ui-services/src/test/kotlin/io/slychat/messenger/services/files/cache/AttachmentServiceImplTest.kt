package io.slychat.messenger.services.files.cache

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.AcceptShareResponse
import io.slychat.messenger.core.http.api.share.ShareAsyncClient
import io.slychat.messenger.core.persistence.FileListMergeResults
import io.slychat.messenger.core.persistence.ReceivedAttachment
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.FileListSyncResult
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.desc
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
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

    private val attachmentCacheManager: AttachmentCacheManager = mock()

    private val networkStatus = BehaviorSubject.create<Boolean>()

    private val messageUpdateEvents = PublishSubject.create<MessageUpdateEvent>()

    private val syncEvents = PublishSubject.create<FileListSyncEvent>()

    @Before
    fun before() {
        whenever(messageService.messageUpdates).thenReturn(messageUpdateEvents)
        whenever(messageService.getAllReceivedAttachments()).thenResolve(emptyList())
        whenever(messageService.completeReceivedAttachments(any())).thenResolve(emptySet())
        whenever(messageService.updateAttachmentInlineState(any())).thenResolve(emptySet())

        whenever(storageService.syncEvents).thenReturn(syncEvents)

        whenever(storageService.downloadFiles(any())).thenResolve(emptyList())

        whenever(shareClient.acceptShare(any(), any())).thenResolve(AcceptShareResponse(emptyMap(), emptyMap(), randomQuota()))
    }

    private fun sendSyncResult(fileId: String, fileMetadata: FileMetadata? = null) {
        val fm = fileMetadata ?: randomFileMetadata()
        val result = FileListSyncResult(
            1,
            FileListMergeResults(
                listOf(randomRemoteFile(fileId = fileId, fileMetadata = fm)),
                emptyList(),
                emptyList()
            ),
            1,
            randomQuota()
        )

        syncEvents.onNext(FileListSyncEvent.Result(result))
    }

    private fun newService(isNetworkAvailable: Boolean = true, init: Boolean = true): AttachmentServiceImpl {
        networkStatus.onNext(isNetworkAvailable)

        val service = AttachmentServiceImpl(
            keyVault,
            tokenManager,
            shareClient,
            messageService,
            storageService,
            attachmentCacheManager,
            networkStatus,
            syncEvents
        )

        if (init)
            service.init()

        return service
    }

    private fun newServiceWithAttachment(attachment: ReceivedAttachment, isNetworkAvailable: Boolean = true, init: Boolean = true): AttachmentServiceImpl {
        whenever(messageService.getAllReceivedAttachments()).thenResolve(listOf(attachment))

        return newService(isNetworkAvailable, init)
    }

    @Test
    fun `it should attempt to accept shares when network is available and addNewReceived is called`() {
        val service = newService()

        val conversationId = randomUserConversationId()
        val sender = randomUserId()
        val messageId = randomMessageId()
        val receivedAttachment = randomReceivedAttachment(0)

        service.addNewReceived(conversationId, sender, listOf(receivedAttachment))

        verify(shareClient).acceptShare(any(), capture {
            assertEquals(it.theirUserId, sender, "Invalid sender")
            assertEquals(1, it.shareInfo.size, "Invalid number of ShareInfo")
            val s = it.shareInfo.first()
            assertEquals(receivedAttachment.theirFileId, s.theirFileId, "Invalid fileId")
            assertEquals(receivedAttachment.theirShareKey, s.theirShareKey, "Invalid share key")
        })
    }

    @Test
    fun `addNewReceived should do nothing when given empty attachments`() {
        val service = newService()

        val conversationId = randomUserConversationId()
        val sender = randomUserId()

        service.addNewReceived(conversationId, sender, emptyList())

        verify(shareClient, never()).acceptShare(any(), any())
    }

    //XXX in this case, we need to recreate a job with the remaining attachments depending on error?
    @Ignore("TODO")
    @Test
    fun `it should handle a subset of accepted attachments failing`() {
        TODO()
    }

    @Test
    fun `it should move to accepting the next attachment once an attachment is complete`() {
        val service = newService()

        val conversationId = randomUserConversationId()
        val sender = randomUserId()
        val receivedAttachment = randomReceivedAttachment(0)
        val receivedAttachment2 = randomReceivedAttachment(0)

        val d = deferred<AcceptShareResponse, Exception>()
        val response = AcceptShareResponse(emptyMap(), emptyMap(), randomQuota())

        whenever(shareClient.acceptShare(any(), any())).thenReturn(d.promise)

        service.addNewReceived(conversationId, sender, listOf(receivedAttachment))

        d.resolve(response)

        service.addNewReceived(conversationId, sender, listOf(receivedAttachment2))

        val captor = argumentCaptor<AcceptShareRequest>()

        verify(shareClient, times(2)).acceptShare(any(), capture(captor))

        val request = captor.allValues[1]

        assertEquals(1, request.shareInfo.size, "Invalid ShareInfo size")
        val s = request.shareInfo.first()

        assertEquals(receivedAttachment2.theirFileId, s.theirFileId, "Invalid fileId")
    }

    @Test
    fun `it should update attachments with the new id upon successfully accepting a request`() {
        val receivedAttachment = randomReceivedAttachment(0)
        val fileId = generateFileId()

        whenever(shareClient.acceptShare(any(), any())).thenResolve(AcceptShareResponse(
            mapOf(receivedAttachment.theirFileId to fileId),
            emptyMap(),
            randomQuota()
        ))

        val service = newServiceWithAttachment(receivedAttachment)

        verify(messageService).completeReceivedAttachments(mapOf(receivedAttachment.id to fileId))
    }

    @Test
    fun `it should emit a file id update event if ids differ when new attachments are accepted`() {
        val receivedAttachment = randomReceivedAttachment(0)
        val fileId = generateFileId()

        whenever(shareClient.acceptShare(any(), any())).thenResolve(AcceptShareResponse(
            mapOf(receivedAttachment.theirFileId to fileId),
            emptyMap(),
            randomQuota()
        ))

        val service = newServiceWithAttachment(receivedAttachment, init = false)

        val testSubscriber = service.events.testSubscriber()

        service.init()

        assertThat(testSubscriber.onNextEvents).desc("Should emit a file id update event") {
            contains(AttachmentEvent.FileIdUpdate(mapOf(receivedAttachment.id to fileId)))
        }
    }

    @Test
    fun `it should not emit a file id update event if ids don't differ when new attachments are accepted`() {
        val receivedAttachment = randomReceivedAttachment(0)

        whenever(shareClient.acceptShare(any(), any())).thenResolve(AcceptShareResponse(
            mapOf(receivedAttachment.theirFileId to receivedAttachment.ourFileId),
            emptyMap(),
            randomQuota()
        ))

        val service = newServiceWithAttachment(receivedAttachment, init = false)

        val testSubscriber = service.events.testSubscriber()

        service.init()

        assertThat(testSubscriber.onNextEvents).isEmpty()
    }

    @Ignore("TODO")
    @Test
    fun `it should start queued attachments when network becomes available`() {
        TODO()
    }

    @Ignore("TODO")
    @Test
    fun `it should remove attachments from queue that correspond to deleted messages`() {
        TODO()
    }

    @Ignore("TODO")
    @Test
    fun `it should remove attachments from queue that correspond to deleted conversations`() {
        TODO()
    }

    //TODO precaching
    
    @Test
    fun `it should emit an inline update event on sync if files meet the inline requirements`() {
        val attachmentId = randomAttachmentId()
        val fileId = generateFileId()
        val fileMetadata = randomFileMetadata(
            fileSize = AttachmentServiceImpl.INLINE_FILE_SIZE_LIMIT,
            mimeType = "image/png"
        )

        val service = newService()

        val testSubscriber = service.events.testSubscriber()

        whenever(messageService.updateAttachmentInlineState(any())).thenResolve(setOf(attachmentId))

        sendSyncResult(fileId, fileMetadata)

        assertThat(testSubscriber.onNextEvents).desc("Should emit an inline update event") {
            contains(AttachmentEvent.InlineUpdate(mapOf(attachmentId to true)))
        }
    }

    @Test
    fun `it should not emit an inline update event on sync if no files meet the file size requirement`() {
        val fileId = generateFileId()
        val fileMetadata = randomFileMetadata(
            fileSize = AttachmentServiceImpl.INLINE_FILE_SIZE_LIMIT + 1,
            mimeType = "image/png"
        )

        val service = newService()

        val testSubscriber = service.events.testSubscriber()

        sendSyncResult(fileId, fileMetadata)

        assertThat(testSubscriber.onNextEvents).isEmpty()
    }

    @Test
    fun `it should not emit an inline update event on sync if no files meet the mime type requirement`() {
        val fileId = generateFileId()
        val fileMetadata = randomFileMetadata(
            fileSize = AttachmentServiceImpl.INLINE_FILE_SIZE_LIMIT,
            mimeType = "application/octet-stream"
        )

        val service = newService()

        val testSubscriber = service.events.testSubscriber()

        sendSyncResult(fileId, fileMetadata)

        assertThat(testSubscriber.onNextEvents).isEmpty()
    }

    //TODO wtf do we do if we're in the middle of accepting an attachment and a file is deleted? we need to delete it after acceptance
}