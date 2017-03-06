package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.MockitoKotlin
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.DownloadPersistenceManager
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject

class DownloaderImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenentTestMode = KovenantTestModeRule()

        init {
            MockitoKotlin.registerInstanceCreator { randomUpload() }
            MockitoKotlin.registerInstanceCreator { randomUserMetadata() }
            MockitoKotlin.registerInstanceCreator { UploadPart(1, 0, 10, false) }
        }
    }

    private val simulDownloads = 10
    private val downloadPersistenceManager: DownloadPersistenceManager = mock()
    private val networkStatus = BehaviorSubject.create<Boolean>()

    private fun newDownloader(isNetworkAvailable: Boolean = true): Downloader {
        networkStatus.onNext(isNetworkAvailable)

        return DownloaderImpl(
            simulDownloads,
            downloadPersistenceManager,
            networkStatus
        )
    }

    private fun randomDownloadInfo(): DownloadInfo {
        val file = randomRemoteFile()
        return DownloadInfo(
            randomDownload(file.id),
            file
        )
    }

    @Before
    fun before() {
        whenever(downloadPersistenceManager.getAll()).thenResolve(emptyList())
    }

    @Test
    fun `it should fetch downloads from storage on init`() {
        val downloader = newDownloader()

        val downloads = listOf(randomDownloadInfo())

        whenever(downloadPersistenceManager.getAll()).thenResolve(downloads)

        downloader.init()

        val actual = downloader.downloads.map { DownloadInfo(it.download, it.file) }

        assertThat(actual).apply {
            describedAs("Should contain initial downloads")
            containsAll(downloads)
        }
    }
}