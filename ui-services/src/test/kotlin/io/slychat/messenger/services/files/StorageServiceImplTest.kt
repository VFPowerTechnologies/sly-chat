package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.MockitoKotlin
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
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

    private val fileListPersistenceManager: FileListPersistenceManager = mock()
    private val storageClient: StorageAsyncClient = mock()
    private val networkStatus = BehaviorSubject.create<Boolean>()

    @Before
    fun before() {
        whenever(storageClient.getQuota(any())).thenResolve(Quota(0, 100))
    }

    private fun newService(isNetworkAvailable: Boolean = true): StorageServiceImpl {
        networkStatus.onNext(isNetworkAvailable)
        return StorageServiceImpl(MockAuthTokenManager(), storageClient, fileListPersistenceManager, networkStatus)
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

        whenever(fileListPersistenceManager.getAllFiles()).thenResolve(fileList)

        assertThat(service.getFileList().get()).apply {
            describedAs("Should return the cached file list")
            containsAll(fileList)
        }
    }

    @Test
    fun `it should queue file deletes when network is unavailable`() {
        TODO()
    }

    @Test
    fun `it should fetch pending changes on init`() {
        TODO()
    }
}