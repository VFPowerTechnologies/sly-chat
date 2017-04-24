package io.slychat.messenger.services.files.cache

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.persistence.AttachmentCachePersistenceManager
import io.slychat.messenger.core.persistence.AttachmentCacheRequest
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomReceivedAttachment
import io.slychat.messenger.services.files.*
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import nl.komponents.kovenant.Promise
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.io.File

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

    private val transferEvents = PublishSubject.create<TransferEvent>()

    private val dummyCachePath = File("/dummy-file")

    @Before
    fun before() {
        whenever(storageService.transferEvents).thenReturn(transferEvents)
        whenever(storageService.downloadFiles(any())).thenAnswer {
            val requests = it.getArgument<List<DownloadRequest>>(0)
            Promise.of(requests.map {
                randomDownloadInfo(fileId = it.fileId)
            })
        }

        whenever(attachmentCachePersistenceManager.getZeroRefCountFiles()).thenResolve(emptyList())
        whenever(attachmentCachePersistenceManager.addRequests(any(), any())).thenResolveUnit()
        whenever(attachmentCachePersistenceManager.updateRequests(any())).thenResolveUnit()

        whenever(attachmentCache.getDownloadPathForFile(any())).thenReturn(dummyCachePath)
        whenever(attachmentCache.delete(any())).thenResolveUnit()
        whenever(attachmentCache.filterPresent(any())).thenResolve(emptySet())

        whenever(thumbnailGenerator.generateThumbnails(any())).thenResolveUnit()
    }

    private fun newManager(): AttachmentCacheManager {
        return AttachmentCacheManagerImpl(
            fileListPersistenceManager,
            storageService,
            attachmentCache,
            attachmentCachePersistenceManager,
            thumbnailGenerator,
            fileEvents
        )
    }

    private fun newManagerWithRequest(request: AttachmentCacheRequest): AttachmentCacheManager {
        whenever(attachmentCachePersistenceManager.getAllRequests()).thenResolve(listOf(request))

        val manager = newManager()

        manager.init()

        return manager
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
    fun `it should start thumbnailing thumbnailing state requests on init`() {
        val fileId = generateFileId()
        val manager = newManagerWithRequest(AttachmentCacheRequest(fileId, null, AttachmentCacheRequest.State.THUMBNAILING))

        verify(thumbnailGenerator).generateThumbnails(fileId)
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
    fun `it should update a request to thumbnailing state when download completes successfully`() {
        val download = randomDownload()
        val request = AttachmentCacheRequest(download.fileId, download.id, AttachmentCacheRequest.State.DOWNLOADING)
        val manager = newManagerWithRequest(request)

        transferEvents.onNext(TransferEvent.StateChanged(download, TransferState.COMPLETE))

        verify(attachmentCachePersistenceManager).updateRequests(listOf(request.copy(state = AttachmentCacheRequest.State.THUMBNAILING)))
    }

    @Test
    fun `it should move a request to the thumbnailing queue when download completes successfully`() {
        val download = randomDownload()
        val request = AttachmentCacheRequest(download.fileId, download.id, AttachmentCacheRequest.State.DOWNLOADING)
        val manager = newManagerWithRequest(request)

        transferEvents.onNext(TransferEvent.StateChanged(download, TransferState.COMPLETE))

        verify(thumbnailGenerator).generateThumbnails(download.fileId)
    }
}