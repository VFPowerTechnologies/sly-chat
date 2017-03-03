package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject

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

        override fun run(): Promise<StorageSyncResult, Exception> {
            return d.promise
        }
    }

    private val fileListPersistenceManager: FileListPersistenceManager = mock()
    private val storageClient: StorageAsyncClient = mock()
    private val networkStatus = BehaviorSubject.create<Boolean>()
    private val syncJobFactory: StorageSyncJobFactory = mock()
    private val syncJob = MockStorageSyncJob()

    @Before
    fun before() {
        whenever(fileListPersistenceManager.deleteFiles(any())).thenResolveUnit()

        whenever(storageClient.getQuota(any())).thenResolve(Quota(0, 100))

        whenever(syncJobFactory.create(any())).thenReturn(syncJob)
    }

    private fun newService(isNetworkAvailable: Boolean = true): StorageServiceImpl {
        networkStatus.onNext(isNetworkAvailable)
        return StorageServiceImpl(MockAuthTokenManager(), storageClient, fileListPersistenceManager, syncJobFactory, networkStatus)
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

        whenever(fileListPersistenceManager.getAllFiles(0, 1000, false)).thenResolve(fileList)

        assertThat(service.getFileList(0, 1000).get()).apply {
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

        val testSubscriber = service.syncRunning.testSubscriber()

        service.sync()

        testSubscriber.assertReceivedOnNext(listOf(true))

        syncJob.d.resolve(StorageSyncResult(0, emptyList(), 0))

        testSubscriber.assertReceivedOnNext(listOf(true, false))
    }
}