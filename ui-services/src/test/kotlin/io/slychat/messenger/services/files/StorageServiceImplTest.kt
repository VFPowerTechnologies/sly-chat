package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.randomQuota
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.services.UserPaths
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
import java.io.File
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
    private val networkStatus = BehaviorSubject.create<Boolean>()
    private val transferManager: TransferManager = mock()
    private val fileAccess: PlatformFileAccess = mock()
    private val syncJobFactory: StorageSyncJobFactory = mock()
    private val syncJob = MockStorageSyncJob()
    private val userPaths = UserPaths(
        File("accountDir"),
        File("keyVault"),
        File("accountInfo"),
        File("accountParams"),
        File("sessionData"),
        File("db"),
        File("config"),
        File("cacheDir")
    )

    private val transferEvents: PublishSubject<TransferEvent> = PublishSubject.create()

    @Before
    fun before() {
        whenever(fileListPersistenceManager.deleteFiles(any())).thenResolveUnit()
        whenever(syncJobFactory.create(any())).thenReturn(syncJob)
        whenever(transferManager.events).thenReturn(transferEvents)
    }

    private fun newService(isNetworkAvailable: Boolean = true): StorageServiceImpl {
        networkStatus.onNext(isNetworkAvailable)
        return StorageServiceImpl(
            MockAuthTokenManager(),
            fileListPersistenceManager,
            syncJobFactory,
            transferManager,
            fileAccess,
            userPaths,
            networkStatus
        )
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

        val result = StorageSyncResult(0, emptyList(), 0, randomQuota())
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
        syncJob.d.resolve(StorageSyncResult(0, emptyList(), 0, randomQuota()))

        transferEvents.onNext(TransferEvent.UploadStateChanged(randomUpload(), TransferState.COMPLETE))

        syncJob.assertRunCalled()
    }

    @Test
    fun `it should queue sync if requested while one is already running`() {
        val service = newService(true)

        service.sync()
        service.sync()

        syncJob.d.resolve(StorageSyncResult(0, emptyList(), 0, randomQuota()))

        assertEquals(2, syncJob.callCount, "Queued sync job not run")
    }

    @Test
    fun `it should update quota after completing a successful sync`() {
        val service = newService(true)

        val testSubscriber = service.quota.testSubscriber()

        service.sync()

        val quota = randomQuota()
        syncJob.d.resolve(StorageSyncResult(0, emptyList(), 0, quota))

        testSubscriber.assertReceivedOnNext(listOf(quota))
    }
}