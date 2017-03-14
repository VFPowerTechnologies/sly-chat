package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.services.crypto.MockAuthTokenManager
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
import kotlin.test.assertEquals

class StorageServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenentTestMode = KovenantTestModeRule()

        init {
            MockitoKotlin.registerInstanceCreator { randomUserMetadata() }
        }
    }

    private class MockStorageSyncJob : StorageSyncJob {
        val d = deferred<StorageSyncResult, Exception>()
        private var wasCalled = false
        var callCount = 0

        override fun run(): Promise<StorageSyncResult, Exception> {
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
    }

    private val fileListPersistenceManager: FileListPersistenceManager = mock()
    private val storageClient: StorageAsyncClient = mock()
    private val networkStatus = BehaviorSubject.create<Boolean>()
    private val transferManager: TransferManager = mock()
    private val fileAccess: PlatformFileAccess = mock()
    private val syncJobFactory: StorageSyncJobFactory = mock()
    private val syncJob = MockStorageSyncJob()

    private val transferEvents: PublishSubject<TransferEvent> = PublishSubject.create()

    @Before
    fun before() {
        whenever(fileListPersistenceManager.deleteFiles(any())).thenResolveUnit()
        whenever(storageClient.getQuota(any())).thenResolve(Quota(0, 100))
        whenever(syncJobFactory.create(any())).thenReturn(syncJob)
        whenever(transferManager.events).thenReturn(transferEvents)
    }

    private fun newService(isNetworkAvailable: Boolean = true): StorageServiceImpl {
        networkStatus.onNext(isNetworkAvailable)
        return StorageServiceImpl(
            MockAuthTokenManager(),
            storageClient,
            fileListPersistenceManager,
            syncJobFactory,
            transferManager,
            fileAccess,
            networkStatus
        )
    }

    @Test
    fun `it should update quota once network becomes available`() {
        val service = newService(false)
        val testSubscriber = service.quota.testSubscriber()

        val quota = Quota(10, 10)
        whenever(storageClient.getQuota(any())).thenResolve(quota)

        networkStatus.onNext(true)

        assertThat(testSubscriber.onNextEvents).apply {
            describedAs("Should emit quota update")
            contains(quota)
        }
    }

    @Test
    fun `it should return the cached file list when getFileList is called`() {
        val service = newService()

        val fileList = listOf(randomRemoteFile(), randomRemoteFile())

        whenever(fileListPersistenceManager.getFiles(0, 1000, false)).thenResolve(fileList)

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
    fun `sync should run on network reconnect`() {
        val service = newService(false)

        networkStatus.onNext(true)

        verify(syncJobFactory).create(any())
    }

    @Test
    fun `sync should update sync status on start and completion`() {
        val service = newService(true)

        val testSubscriber = service.syncEvents.testSubscriber()

        service.sync()

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.Begin()))

        val result = StorageSyncResult(0, emptyList(), 0)
        syncJob.d.resolve(result)

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.Begin(), FileListSyncEvent.End(result)))
    }

    @Test
    fun `sync should emit an Error event if the sync fails`() {
        val service = newService(true)

        val testSubscriber = service.syncEvents.testSubscriber()

        service.sync()

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.Begin()))

        syncJob.d.reject(TestException())

        testSubscriber.assertReceivedOnNext(listOf(FileListSyncEvent.Begin(), FileListSyncEvent.Error()))
    }

    @Test
    fun `getFilesAt should proxy requests to the persistence manager`() {
        val service = newService(true)

        val files = listOf(randomRemoteFile())

        val startingAt = 0
        val count = 100
        val path = "/"

        whenever(fileListPersistenceManager.getFilesAt(startingAt, count, false, path)).thenResolve(files)

        assertEquals(files, service.getFilesAt(startingAt, count, path).get(), "Returned invalid files")
    }

    @Test
    fun `getFiles should proxy requests to the persistence manager`() {
        val service = newService(true)

        val files = listOf(randomRemoteFile())

        val startingAt = 0
        val count = 100

        whenever(fileListPersistenceManager.getFiles(startingAt, count, false)).thenResolve(files)

        assertEquals(files, service.getFiles(startingAt, count).get(), "Returned invalid files")
    }

    @Test
    fun `it should sync when receiving an upload completion event`() {
        val service = newService(true)

        //since we sync on startup
        syncJob.clearCalls()
        syncJob.d.resolve(StorageSyncResult(0, emptyList(), 0))

        transferEvents.onNext(TransferEvent.UploadStateChanged(randomUpload(), TransferState.COMPLETE))

        syncJob.assertRunCalled()
    }

    @Test
    fun `it should queue sync if requested while one is already running`() {
        val service = newService(true)

        service.sync()

        syncJob.d.resolve(StorageSyncResult(0, emptyList(), 0))

        assertEquals(2, syncJob.callCount, "Queued sync job not run")
    }
}