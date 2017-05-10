package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.persistence.FileListMergeResults
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.services.files.cache.AttachmentCache
import io.slychat.messenger.testutils.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class StorageServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenentTestMode = KovenantTestModeRule()

        init {
            MockitoKotlin.registerInstanceCreator { randomUserMetadata() }
            MockitoKotlin.registerInstanceCreator { randomUpload() }
            MockitoKotlin.registerInstanceCreator { randomUploadInfo() }
        }
    }

    private class MockStorageSyncJob : StorageSyncJob {
        val d = deferred<FileListSyncResult, Exception>()
        private var wasCalled = false
        var callCount = 0

        override fun run(): Promise<FileListSyncResult, Exception> {
            wasCalled = true
            callCount += 1
            return d.promise
        }

        fun clearCalls() {
            wasCalled = false
            callCount = 0
        }

        fun assertRunCalled() {
            if (!wasCalled)
                fail("StorageSyncJob.run() not called")
        }

        fun assertRunNotCalled() {
            if (wasCalled)
                fail("StorageSyncJob.run() was called")
        }
    }

    private val fileListPersistenceManager: FileListPersistenceManager = mock()
    private val networkStatus = BehaviorSubject.create<Boolean>()
    private val transferManager: TransferManager = mock()
    private val fileAccess: PlatformFileAccess = mock()
    private val syncJobFactory: StorageSyncJobFactory = mock()
    private val syncJob = MockStorageSyncJob()
    private val attachmentCache: AttachmentCache = mock()

    private val dummyCachePath = File("/cachePath")

    private val transferEvents = PublishSubject.create<TransferEvent>()
    private val quotaEvents = PublishSubject.create<Quota>()

    @Before
    fun before() {
        whenever(fileListPersistenceManager.deleteFiles(any())).thenResolve(emptyList())
        whenever(syncJobFactory.create(any())).thenReturn(syncJob)
        whenever(transferManager.events).thenReturn(transferEvents)
        whenever(transferManager.quota).thenReturn(quotaEvents)
        whenever(transferManager.transfers).thenReturn(emptyList())
        whenever(transferManager.remove(any())).thenResolveUnit()
        whenever(transferManager.upload(any())).thenResolveUnit()
        whenever(transferManager.download(any())).thenResolveUnit()
        whenever(fileAccess.getFileInfo(any())).thenReturn(FileInfo("displayName", randomLong(), "*/*"))

        whenever(attachmentCache.getFinalPathForFile(any())).thenReturn(dummyCachePath)
    }

    private fun newService(isNetworkAvailable: Boolean = true): StorageServiceImpl {
        networkStatus.onNext(isNetworkAvailable)
        return StorageServiceImpl(
            MockAuthTokenManager(),
            fileListPersistenceManager,
            syncJobFactory,
            transferManager,
            fileAccess,
            attachmentCache,
            networkStatus
        )
    }

    @Test
    fun `it should return the cached file list when getFileList is called`() {
        val service = newService()

        val fileList = listOf(randomRemoteFile(), randomRemoteFile())

        whenever(fileListPersistenceManager.getFiles(0, 1000, false, false)).thenResolve(fileList)

        assertThat(service.getFiles(0, 1000).get()).apply {
            describedAs("Should return the cached file list")
            containsAll(fileList)
        }
    }

    @Test
    fun `it should persist file deletes`() {
        val service = newService(false)

        val fileIds = listOf(generateFileId())

        service.deleteFiles(fileIds).get()

        verify(fileListPersistenceManager).deleteFiles(fileIds)
    }

    @Test
    fun `it should run a sync after a file deletes when network is available`() {
        val service = newService(true)

        service.deleteFiles(listOf(generateFileId())).get()

        verify(syncJobFactory).create(any())
    }

    @Test
    fun `sync should not run if network is unavailable`() {
        val service = newService(false)

        service.sync()

        verify(syncJobFactory, never()).create(any())
    }

    @Test
    fun `sync should queue a sync to on network reconnect`() {
        val service = newService(false)

        service.sync()

        verify(syncJobFactory, never()).create(any())

        networkStatus.onNext(true)

        verify(syncJobFactory).create(any())
    }

    @Test
    fun `sync should emit events on start and completion`() {
        val service = newService(true)

        val result = FileListSyncResult(0, FileListMergeResults.empty, 0, randomQuota())
        syncJob.d.resolve(result)

        val testSubscriber = service.syncEvents.testSubscriber()

        service.sync()

        val events = listOf(
            FileListSyncEvent.Begin(),
            FileListSyncEvent.Result(result),
            FileListSyncEvent.End(false)
        )
        testSubscriber.assertReceivedOnNext(events)
    }

    @Test
    fun `sync should emit an End event with hasError if the sync fails`() {
        val service = newService(true)

        val testSubscriber = service.syncEvents.testSubscriber()

        service.sync()

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.Begin()))

        syncJob.d.reject(TestException())

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.Begin(), FileListSyncEvent.End(true)))
    }

    @Test
    fun `clearSyncError should emit an End event with no error indication`() {
        val service = newService(true)

        service.sync()

        syncJob.d.reject(TestException())

        val testSubscriber = service.syncEvents.testSubscriber()

        service.clearSyncError()

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.End(true), FileListSyncEvent.End(false)))
    }

    @Test
    fun `getFilesAt should proxy requests to the persistence manager`() {
        val service = newService(true)

        val files = listOf(randomRemoteFile())

        val startingAt = 0
        val count = 100
        val path = "/"

        whenever(fileListPersistenceManager.getFilesAt(startingAt, count, false, false, path)).thenResolve(files)

        assertEquals(files, service.getFilesAt(startingAt, count, path).get(), "Returned invalid files")
    }

    @Test
    fun `getFiles should proxy requests to the persistence manager`() {
        val service = newService(true)

        val files = listOf(randomRemoteFile())

        val startingAt = 0
        val count = 100

        whenever(fileListPersistenceManager.getFiles(startingAt, count, false, false)).thenResolve(files)

        assertEquals(files, service.getFiles(startingAt, count).get(), "Returned invalid files")
    }

    @Test
    fun `it should sync when receiving an upload completion event`() {
        val service = newService(true)

        transferEvents.onNext(TransferEvent.StateChanged(randomUpload(), TransferState.COMPLETE))

        syncJob.assertRunCalled()
    }

    @Test
    fun `it should sync when receiving an upload cancellation event`() {
        val service = newService(true)

        transferEvents.onNext(TransferEvent.StateChanged(randomUpload(), TransferState.CANCELLED))

        syncJob.assertRunCalled()
    }

    @Test
    fun `it should not sync when receiving a download completion event`() {
        val service = newService(true)

        transferEvents.onNext(TransferEvent.StateChanged(randomDownload(), TransferState.COMPLETE))

        syncJob.assertRunNotCalled()
    }

    @Test
    fun `it should queue sync if requested while one is already running`() {
        val service = newService(true)

        service.sync()
        service.sync()

        syncJob.d.resolve(FileListSyncResult(0, FileListMergeResults.empty, 0, randomQuota()))

        assertEquals(2, syncJob.callCount, "Queued sync job not run")
    }

    @Test
    fun `it should update quota after completing a successful sync`() {
        val service = newService(true)

        val testSubscriber = service.quota.testSubscriber()

        service.sync()

        val quota = randomQuota()
        syncJob.d.resolve(FileListSyncResult(0, FileListMergeResults.empty, 0, quota))

        testSubscriber.assertReceivedOnNext(listOf(quota))
    }

    @Test
    fun `it should proxy quota info emitted by TransferManager`() {
        val service = newService(true)

        val testSubscriber = service.quota.testSubscriber()

        val quota = randomQuota()

        quotaEvents.onNext(quota)

        testSubscriber.assertReceivedOnNext(listOf(quota))
    }

    @Test
    fun `it should emit a file deleted event when a local file is marked as deleted`() {
        val service = newService(true)

        val file = randomRemoteFile(isDeleted = true)

        val testSubscriber = service.fileEvents.testSubscriber()

        whenever(fileListPersistenceManager.deleteFiles(any())).thenResolve(listOf(file))

        service.deleteFiles(listOf(file.id)).get()

        testSubscriber.assertReceivedOnNext(listOf(RemoteFileEvent.Deleted(listOf(file))))
    }

    private fun testSyncFileEvents(mergeResults: FileListMergeResults) {
        val service = newService(true)

        val testSubscriber = service.fileEvents.testSubscriber()

        service.sync()

        syncJob.d.resolve(FileListSyncResult(0, mergeResults, 0, randomQuota()))

        val ev = if (mergeResults.added.isNotEmpty())
            RemoteFileEvent.Added(mergeResults.added)
        else if (mergeResults.deleted.isNotEmpty())
            RemoteFileEvent.Deleted(mergeResults.deleted)
        else if (mergeResults.updated.isNotEmpty())
            RemoteFileEvent.Updated(mergeResults.updated)
        else
            error("Received empty merge results")

        testSubscriber.assertReceivedOnNext(listOf(ev))
    }

    @Test
    fun `it should emit added events when a sync has added results`() {
        testSyncFileEvents(FileListMergeResults(listOf(randomRemoteFile()), emptyList(), emptyList()))
    }

    @Test
    fun `it should emit deleted events when a sync has deleted results`() {
        testSyncFileEvents(FileListMergeResults(emptyList(), listOf(randomRemoteFile()), emptyList()))
    }

    @Test
    fun `it should emit updated events when a sync has updated results`() {
        testSyncFileEvents(FileListMergeResults(emptyList(), emptyList(), listOf(randomRemoteFile())))
    }

    @Test
    fun `it should not emit a file added event when an upload store fails`() {
        val service = newService(true)

        val testSubscriber = service.fileEvents.testSubscriber()

        whenever(transferManager.upload(any())).thenReject(TestException())

        assertFails {
            service.uploadFile("/localPath", "/remoteDir", "fileName", false).get()
        }

        val events = testSubscriber.onNextEvents.filter { it is RemoteFileEvent.Added }
        assertThat(events).desc("It should not emit an Added event") {
            isEmpty()
        }
    }

    //TODO we need to call markOriginalComplete when CacheOperation completes as well
    //or maybe we could omit the cache path now and pull it during upload later? but then we need Uploader to be able to handle this somehow
    //we also need to trigger caching via requestCache when sending a message with an attachment
    @Test
    fun `uploadFile should use the cache dir cache is requested`() {
        val service = newService(true)

        service.uploadFile("/localPath", "/remoteDir", "fileName", true).get()

        verify(transferManager).upload(capture {
            assertEquals(dummyCachePath.path, it.upload.cachePath, "Invalid cache path")
        })
    }

    @Test
    fun `it should remove any associated downloads when deleting a file`() {
        val service = newService(false)

        val status = randomDownloadTransferStatus(TransferState.COMPLETE)

        val fileIds = listOf(
            generateFileId(),
            status.file!!.id
        )

        whenever(transferManager.transfers).thenReturn(listOf(status))

        service.deleteFiles(fileIds).get()

        verify(transferManager).remove(listOf(status.id))
        verify(fileListPersistenceManager).deleteFiles(fileIds)
    }

    @Test
    fun `it should remove any associated uploads when deleting a file`() {
        val service = newService(false)

        val status = randomUploadTransferStatus(TransferState.COMPLETE)

        val fileIds = listOf(
            generateFileId(),
            status.file!!.id
        )

        whenever(transferManager.transfers).thenReturn(listOf(status))

        service.deleteFiles(fileIds).get()

        verify(transferManager).remove(listOf(status.id))
        verify(fileListPersistenceManager).deleteFiles(fileIds)
    }

    private fun testDownloadDecryptionSetting(doDecrypt: Boolean) {
        val service = newService(false)

        val request = DownloadRequest(generateFileId(), "/localPath", doDecrypt)

        val file = randomRemoteFile(request.fileId)

        whenever(fileListPersistenceManager.getFilesById(any())).thenResolve(mapOf(file.id to file))

        val results = service.downloadFiles(listOf(request, request)).get()

        val info = assertNotNull(results.firstOrNull(), "Empty download info")

        assertEquals(doDecrypt, info.download.doDecrypt, "Decryption setting not respected")
    }

    @Test
    fun `downloadFile should obey request decryption setting (false)`() {
        testDownloadDecryptionSetting(false)
    }

    @Test
    fun `downloadFile should obey request decryption setting (true)`() {
        testDownloadDecryptionSetting(true)
    }
}