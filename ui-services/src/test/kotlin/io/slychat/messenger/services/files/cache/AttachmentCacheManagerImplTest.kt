package io.slychat.messenger.services.files.cache

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.AttachmentCachePersistenceManager
import io.slychat.messenger.core.persistence.AttachmentCacheRequest
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.files.*
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentCacheManagerImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestModeRule = KovenantTestModeRule()
    }

    private val fileListPersistenceManager: FileListPersistenceManager = mock()

    private val storageService: StorageService = mock()

    private val attachmentCache: AttachmentCache = mock()

    private val attachmentCachePersistenceManager: AttachmentCachePersistenceManager = mock()

    private val thumbnailGenerator: ThumbnailGenerator = mock()

    private val fileEvents = PublishSubject.create<RemoteFileEvent>()

    private val messageUpdateEvents = PublishSubject.create<MessageUpdateEvent>()

    private val transferEvents = PublishSubject.create<TransferEvent>()

    private val dummyCachePath = File("/dummy-file")

    private fun dummyThumbnailStreams(): ThumbnailWriteStreams {
        return ThumbnailWriteStreams(ByteArrayInputStream(emptyByteArray()), ByteArrayOutputStream())
    }

    @Before
    fun before() {
        whenever(storageService.transferEvents).thenReturn(transferEvents)
        whenever(storageService.downloadFiles(any())).thenAnswer {
            val requests = it.getArgument<List<DownloadRequest>>(0)
            Promise.of(requests.map {
                randomDownloadInfo(fileId = it.fileId)
            })
        }

        whenever(attachmentCachePersistenceManager.getAllRequests()).thenResolve(emptyList())
        whenever(attachmentCachePersistenceManager.getZeroRefCountFiles()).thenResolve(emptyList())
        whenever(attachmentCachePersistenceManager.addRequests(any(), any())).thenResolveUnit()
        whenever(attachmentCachePersistenceManager.updateRequests(any())).thenResolveUnit()
        whenever(attachmentCachePersistenceManager.deleteRequests(any())).thenResolveUnit()

        whenever(attachmentCache.getDownloadPathForFile(any())).thenReturn(dummyCachePath)
        whenever(attachmentCache.delete(any())).thenResolveUnit()
        whenever(attachmentCache.filterPresent(any())).thenResolve(emptySet())
        whenever(attachmentCache.isOriginalPresent(any())).thenReturn(true)
        whenever(attachmentCache.markOriginalComplete(any())).thenResolveUnit()

        whenever(thumbnailGenerator.generateThumbnail(any(), any(), any())).thenResolveUnit()
    }

    private fun newManager(): AttachmentCacheManager {
        return AttachmentCacheManagerImpl(
            fileListPersistenceManager,
            storageService,
            attachmentCache,
            attachmentCachePersistenceManager,
            thumbnailGenerator,
            fileEvents,
            messageUpdateEvents
        )
    }

    private fun newManagerWithRequest(request: AttachmentCacheRequest): AttachmentCacheManager {
        whenever(attachmentCachePersistenceManager.getAllRequests()).thenResolve(listOf(request))

        val manager = newManager()

        manager.init()

        return manager
    }

    private fun testDeletedMessageGetZeroRefCountFiles(ev: MessageUpdateEvent) {
        val manager = newManager()

        messageUpdateEvents.onNext(ev)

        verify(attachmentCachePersistenceManager).getZeroRefCountFiles()
    }

    private fun testDeletedMessageTransferCancellation(ev: MessageUpdateEvent) {
        val manager = newManager()

        val transfer = randomDownloadTransferStatus(TransferState.QUEUED)

        val fileIds = listOf(transfer.file!!.id)

        whenever(storageService.transfers).thenReturn(listOf(transfer))

        whenever(attachmentCachePersistenceManager.getZeroRefCountFiles()).thenResolve(fileIds)

        messageUpdateEvents.onNext(ev)

        verify(storageService).cancel(listOf(transfer.id))
    }

    @Test
    fun `it should start downloading pending state requests on init`() {
        val fileId = generateFileId()
        val manager = newManagerWithRequest(AttachmentCacheRequest(fileId, null, AttachmentCacheRequest.State.PENDING))

        verify(storageService).downloadFiles(listOf(DownloadRequest(fileId, dummyCachePath.path)))
    }

    @Test
    fun `it should delete files with zero ref count on init`() {
        val fileIds = listOf(generateFileId())
        whenever(attachmentCachePersistenceManager.getZeroRefCountFiles()).thenResolve(fileIds)

        val manager = newManager()

        manager.init()

        verify(attachmentCache).delete(fileIds)
    }

    @Test
    fun `requestCache should add new requests to download queue`() {
        val manager = newManager()

        val attachment = randomReceivedAttachment()

        manager.requestCache(listOf(attachment)).get()

        verify(storageService).downloadFiles(any())
    }

    @Test
    fun `requestCache should persist cache requests`() {
        val manager = newManager()

        val attachment = randomReceivedAttachment()

        manager.requestCache(listOf(attachment)).get()

        val request = AttachmentCacheRequest(attachment.fileId, null, AttachmentCacheRequest.State.PENDING)
        verify(attachmentCachePersistenceManager).addRequests(listOf(request), emptyList())
    }

    @Test
    fun `requestCache should not add new requests when an active request exists for a file`() {
        TODO()
    }

    @Test
    fun `requestCache should not add new requests when the original file is already cached on disk`() {
        TODO()
    }

    @Test
    fun `it should update stored request when a download is is assigned`() {
        val info = randomDownloadInfo()
        val download = info.download

        whenever(storageService.downloadFiles(listOf(DownloadRequest(download.fileId, dummyCachePath.path)))).thenResolve(listOf(info))

        val manager = newManagerWithRequest(AttachmentCacheRequest(download.fileId, null, AttachmentCacheRequest.State.PENDING))

        val expected = AttachmentCacheRequest(download.fileId, download.id, AttachmentCacheRequest.State.DOWNLOADING)
        verify(attachmentCachePersistenceManager).updateRequests(listOf(expected))
    }

    @Test
    fun `it should ignore transfer state events for untracked files`() {
        val download = randomDownload()
        val manager = newManager()

        transferEvents.onNext(TransferEvent.StateChanged(download, TransferState.COMPLETE))

        verify(attachmentCachePersistenceManager, never()).updateRequests(any())
    }

    @Test
    fun `it should not requeue a stored request in downloading state`() {
        val download = randomDownload()
        val request = AttachmentCacheRequest(download.fileId, download.id, AttachmentCacheRequest.State.DOWNLOADING)
        val manager = newManagerWithRequest(request)

        verify(attachmentCachePersistenceManager, never()).updateRequests(any())
    }

    @Test
    fun `it should delete a request when download completes successfully`() {
        val download = randomDownload()
        val request = AttachmentCacheRequest(download.fileId, download.id, AttachmentCacheRequest.State.DOWNLOADING)
        val manager = newManagerWithRequest(request)

        transferEvents.onNext(TransferEvent.StateChanged(download, TransferState.COMPLETE))

        verify(attachmentCachePersistenceManager).deleteRequests(listOf(download.fileId))
    }

    @Test
    fun `it should mark a cache file as complete when download completes successfully`() {
        val download = randomDownload()
        val request = AttachmentCacheRequest(download.fileId, download.id, AttachmentCacheRequest.State.DOWNLOADING)
        val manager = newManagerWithRequest(request)

        transferEvents.onNext(TransferEvent.StateChanged(download, TransferState.COMPLETE))

        verify(attachmentCache).markOriginalComplete(listOf(download.fileId))
    }

    @Test
    fun `requesting an original image not in the cache should trigger a download if the file isn't marked as deleted`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)
        whenever(attachmentCache.getOriginalImageInputStream(any(), any(), any(), any())).thenReturn(null)

        val result = manager.getImageStream(fileId).get()
        assertNull(result.inputStream)
        assertFalse(result.isDeleted)

        val request = AttachmentCacheRequest(fileId, null, AttachmentCacheRequest.State.PENDING)
        verify(attachmentCachePersistenceManager).addRequests(listOf(request), emptyList())
    }

    @Test
    fun `requesting an original image should return a stream if the file is cached`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id

        val stream = ByteArrayInputStream(emptyByteArray())

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)
        whenever(attachmentCache.getOriginalImageInputStream(any(), any(), any(), any())).thenReturn(stream)

        val result = manager.getImageStream(fileId).get()
        assertNotNull(result.inputStream, "Should return an InputStream")
        assertTrue(result.inputStream === stream, "Invalid InputStream")
        assertFalse(result.isDeleted, "Shouldn't be marked as deleted")
    }

    @Test
    fun `requesting an original image not in the cache should not trigger a download if the file is deleted`() {
        val manager = newManager()

        val file = randomRemoteFile(isDeleted = true)
        val fileId = file.id

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)
        whenever(attachmentCache.getOriginalImageInputStream(any(), any(), any(), any())).thenReturn(null)

        val result = manager.getImageStream(fileId).get()
        assertNull(result.inputStream)
        assertTrue(result.isDeleted, "File should be listed as deleted")

        verify(attachmentCachePersistenceManager, never()).addRequests(any(), any())
    }

    @Test
    fun `it should not queue duplicate downloading jobs when getImageStream is called`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)
        whenever(attachmentCache.getOriginalImageInputStream(any(), any(), any(), any())).thenReturn(null)

        manager.getImageStream(fileId).get()
        manager.getImageStream(fileId).get()

        verify(attachmentCachePersistenceManager, times(1)).addRequests(any(), any())
    }

    @Test
    fun `requesting a thumbnail not in the cache should trigger a thumbnailing operation if the file isn't deleted`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id
        val resolution = 200

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)
        whenever(attachmentCache.getThumbnailInputStream(eq(fileId), eq(resolution), any(), any(), any())).thenReturn(null)
        whenever(attachmentCache.getThumbnailGenerationStreams(eq(fileId), eq(resolution), any(), any(), any())).thenReturn(dummyThumbnailStreams())

        val result = manager.getThumbnailStream(fileId, resolution).get()
        assertNull(result.inputStream)
        assertFalse(result.isDeleted, "File not should be listed as deleted")

        verify(thumbnailGenerator).generateThumbnail(any(), any(), eq(resolution))
    }

    @Test
    fun `requesting a thumbnail image should return a stream if the thumbnail is cached`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id

        val stream = ByteArrayInputStream(emptyByteArray())

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)
        whenever(attachmentCache.getThumbnailInputStream(any(), any(), any(), any(), any())).thenReturn(stream)

        val result = manager.getThumbnailStream(fileId, 200).get()
        assertNotNull(result.inputStream, "Should return an InputStream")
        assertTrue(result.inputStream === stream, "Invalid InputStream")
        assertFalse(result.isDeleted, "Shouldn't be marked as deleted")
    }

    @Test
    fun `it should not queue duplicate thumbnailing jobs`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id
        val resolution = 200

        whenever(attachmentCache.getThumbnailInputStream(eq(fileId), eq(resolution), any(), any(), any())).thenReturn(null)
        whenever(attachmentCache.getThumbnailGenerationStreams(eq(fileId), eq(resolution), any(), any(), any())).thenReturn(dummyThumbnailStreams())

        val d = deferred<RemoteFile?, Exception>()
        val d2 = deferred<RemoteFile?, Exception>()
        whenever(fileListPersistenceManager.getFile(fileId))
            //first two are for the first job; second is for the second
            .thenResolve(file)
            .thenReturn(d.promise)
            .thenReturn(d2.promise)

        manager.getThumbnailStream(fileId, resolution)

        val p2 = manager.getThumbnailStream(fileId, resolution)

        d2.resolve(file)

        p2.get()

        verify(thumbnailGenerator, never()).generateThumbnail(any(), any(), eq(resolution))
    }

    @Test
    fun `requesting a thumbnail not in the cache should not trigger a thumbnailing operation if the file is deleted`() {
        val manager = newManager()

        val file = randomRemoteFile(isDeleted = true)
        val fileId = file.id

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)

        val result = manager.getThumbnailStream(fileId, 200).get()
        assertNull(result.inputStream)
        assertTrue(result.isDeleted, "File should be listed as deleted")
    }

    @Test
    fun `requesting a thumbnail when the original is not in the cache should trigger a download if the file isn't deleted`() {
        val manager = newManager()

        val file = randomRemoteFile()
        val fileId = file.id

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)

        whenever(attachmentCache.isOriginalPresent(fileId)).thenReturn(false)
        whenever(attachmentCache.getThumbnailInputStream(any(), any(), any(), any(), any())).thenReturn(null)

        val resolution = 200
        val result = manager.getThumbnailStream(fileId, resolution).get()
        assertNull(result.inputStream)
        assertFalse(result.isDeleted)
        assertFalse(result.isOriginalPresent)

        verify(storageService).downloadFiles(listOf(DownloadRequest(fileId, dummyCachePath.path)))
    }

    @Test
    fun `a requested thumbnail should be generated after a download completes successfully`() {
        val manager = newManager()

        val resolution = 200
        val file = randomRemoteFile()
        val fileId = file.id
        val info = randomDownloadInfo(fileId = fileId)

        whenever(fileListPersistenceManager.getFile(fileId)).thenResolve(file)

        whenever(attachmentCache.isOriginalPresent(fileId)).thenReturn(false)
        whenever(attachmentCache.getThumbnailInputStream(any(), any(), any(), any(), any())).thenReturn(null)
        whenever(attachmentCache.getThumbnailGenerationStreams(eq(fileId), eq(resolution), any(), any(), any())).thenReturn(dummyThumbnailStreams())

        whenever(storageService.downloadFiles(listOf(DownloadRequest(fileId, dummyCachePath.path)))).thenResolve(listOf(info))

        manager.getThumbnailStream(fileId, resolution).get()

        transferEvents.onNext(TransferEvent.StateChanged(info.download, TransferState.COMPLETE))

        verify(thumbnailGenerator).generateThumbnail(any(), any(), eq(resolution))
    }

    @Test
    fun `a message Deleted event should trigger a check for zero ref'ed files`() {
        val ev = MessageUpdateEvent.Deleted(randomUserConversationId(), randomMessageIds(), false)
        testDeletedMessageGetZeroRefCountFiles(ev)
    }

    @Test
    fun `a message DeletedAll event should trigger a check for zero ref'ed files`() {
        val ev = MessageUpdateEvent.DeletedAll(randomUserConversationId(), 1, false)
        testDeletedMessageGetZeroRefCountFiles(ev)
    }

    @Test
    fun `a message Deleted event should should cancellation of associated downloads`() {
        val ev = MessageUpdateEvent.Deleted(randomUserConversationId(), randomMessageIds(), false)
        testDeletedMessageTransferCancellation(ev)
    }

    @Test
    fun `a message DeletedAll event should trigger cancellation of associated downloads`() {
        val ev = MessageUpdateEvent.DeletedAll(randomUserConversationId(), 1, false)
        testDeletedMessageTransferCancellation(ev)
    }

    @Test
    fun `it should cancel associated downloads if a file is deleted`() {
        val transfer = randomDownloadTransferStatus(TransferState.QUEUED)
        val file = randomRemoteFile(fileId = transfer.file!!.id, isDeleted = true)

        whenever(storageService.transfers).thenReturn(listOf(transfer))

        val ev = RemoteFileEvent.Deleted(listOf(file))

        val manager = newManager()

        fileEvents.onNext(ev)

        verify(storageService).cancel(listOf(transfer.id))
    }
}