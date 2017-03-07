package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    private val downloadOperations = MockDownloadOperations()

    private fun newDownloader(isNetworkAvailable: Boolean = true): Downloader {
        return DownloaderImpl(
            simulDownloads,
            downloadPersistenceManager,
            downloadOperations,
            isNetworkAvailable
        )
    }

    private fun <R> assertEventEmitted(downloader: Downloader, event: TransferEvent, body: () -> R): R {
        val testSubscriber = downloader.events.testSubscriber()

        val r = body()

        assertThat(testSubscriber.onNextEvents).apply {
            describedAs("Should emit ${event.javaClass.simpleName}")
            contains(event)
        }

        return r
    }

    private fun randomDownloadInfo(error: DownloadError? = null, state: DownloadState = DownloadState.CREATED): DownloadInfo {
        val file = randomRemoteFile()
        return DownloadInfo(
            randomDownload(file.id, error = error, state = state),
            file
        )
    }

    private fun testClearError(manager: Downloader): DownloadInfo {
        val info = randomDownloadInfo(error = DownloadError.REMOTE_FILE_MISSING)

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(info))

        manager.init()

        manager.clearError(info.download.id).get()

        return info.copy(
            download = info.download.copy(error = null)
        )
    }

    private fun testDownloadError(e: Exception, expectedError: DownloadError) {
        val downloader = newDownloader(true)

        val downloadInfo = randomDownloadInfo()
        downloader.download(downloadInfo).get()

        val download = downloadInfo.download
        val d = downloadOperations.getDownloadDeferred(download.id)

        val event = TransferEvent.DownloadStateChange(download.copy(error = expectedError), TransferState.ERROR)
        assertEventEmitted(downloader, event) {
            d.reject(e)
        }

        verify(downloadPersistenceManager).setError(download.id, expectedError)
    }

    @Before
    fun before() {
        whenever(downloadPersistenceManager.add(any())).thenResolveUnit()
        whenever(downloadPersistenceManager.getAll()).thenResolve(emptyList())
        whenever(downloadPersistenceManager.setState(any(), any())).thenResolveUnit()
        whenever(downloadPersistenceManager.setError(any(), any())).thenResolveUnit()
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

    @Test
    fun `it should not queue initial downloads with errors`() {
        val downloadInfo = randomDownloadInfo(DownloadError.NETWORK_ISSUE)

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(downloadInfo))

        val downloader = newDownloader()
        downloader.init()

        downloadOperations.assertDownloadNotCalled()
    }

    @Test
    fun `it should start fetched incomplete downloads with no error when network is available`() {
        val downloadInfo = randomDownloadInfo()

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(downloadInfo))

        val downloader = newDownloader()
        downloader.init()

        downloadOperations.assertDownloadCalled(downloadInfo.download, downloadInfo.file)
    }

    @Test
    fun `it should not start fetched completed downloads`() {
        val downloadInfo = randomDownloadInfo(state = DownloadState.COMPLETE)

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(downloadInfo))

        val downloader = newDownloader()
        downloader.init()

        downloadOperations.assertDownloadNotCalled()
    }

    @Test
    fun `it should emit DownloadAdded with state=ERROR when fetching downloads with an associated error`() {
        val downloadInfo = randomDownloadInfo(error = DownloadError.REMOTE_FILE_MISSING)

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(downloadInfo))

        val downloader = newDownloader()
        val event = TransferEvent.DownloadAdded(downloadInfo.download, TransferState.ERROR)
        assertEventEmitted(downloader, event) {
            downloader.init()
        }
    }

    @Test
    fun `adding a new download should persist the download info`() {
        val downloader = newDownloader(false)

        val downloadInfo = randomDownloadInfo()

        downloader.download(downloadInfo).get()

        verify(downloadPersistenceManager).add(downloadInfo.download)
    }

    @Test
    fun `adding a new download should emit a DownloadAdded event`() {
        val downloader = newDownloader(false)

        val downloadInfo = randomDownloadInfo()

        val event = TransferEvent.DownloadAdded(downloadInfo.download, TransferState.QUEUED)
        assertEventEmitted(downloader, event) {
            downloader.download(downloadInfo).get()
        }
    }

    @Test
    fun `it should start queued downloads when network becomes available`() {
        val downloader = newDownloader(false)

        val downloadInfo = randomDownloadInfo()

        downloader.download(downloadInfo).get()

        downloader.isNetworkAvailable = true

        downloadOperations.assertDownloadCalled(downloadInfo.download, downloadInfo.file)
    }

    @Test
    fun `it should emit a TransferEvent when moving a download to completion state`() {
        val downloader = newDownloader(true)

        val downloadInfo = randomDownloadInfo()
        downloader.download(downloadInfo).get()

        val download = downloadInfo.download
        val d = downloadOperations.getDownloadDeferred(download.id)

        val event = TransferEvent.DownloadStateChange(download.copy(state = DownloadState.COMPLETE), TransferState.COMPLETE)
        assertEventEmitted(downloader, event) {
            d.resolve(Unit)
        }
    }

    @Test
    fun `it should mark a download as completed when a download completes successfully`() {
        val downloader = newDownloader(true)

        val downloadInfo = randomDownloadInfo()
        downloader.download(downloadInfo).get()

        val d = downloadOperations.getDownloadDeferred(downloadInfo.download.id)
        d.resolve(Unit)

        verify(downloadPersistenceManager).setState(downloadInfo.download.id, DownloadState.COMPLETE)
    }

    @Test
    fun `it should set the download error to REMOTE_FILE_MISSING when download fails with FileMissingException`() {
        testDownloadError(FileMissingException("x"), DownloadError.REMOTE_FILE_MISSING)
    }

    @Test
    fun `it should set the download error to NETWORK_ERROR when download fails with a network error`() {
        testDownloadError(SocketTimeoutException(), DownloadError.NETWORK_ISSUE)
    }

    @Test
    fun `it should set the download error to CANCELLED when download fails with CancellationException`() {
        testDownloadError(CancellationException(), DownloadError.CANCELLED)
    }

    @Test
    fun `clearError should clear the stored update error`() {
        val downloader = newDownloader(true)

        val info = testClearError(downloader)
        verify(downloadPersistenceManager).setError(info.download.id, null)
    }

    @Test
    fun `clearError should reset the cached download's error state`() {
        val downloader = newDownloader(true)

        val info = testClearError(downloader)

        val status = assertNotNull(downloader.downloads.find { it.download.id == info.download.id }, "Download not found")

        assertNull(status.download.error, "Error not cleared")
    }

    @Test
    fun `clearError should update emit a TransferEvent on clear`() {
        val downloader = newDownloader(true)
        val info = randomDownloadInfo(error = DownloadError.REMOTE_FILE_MISSING)

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(info))

        downloader.init()

        assertEventEmitted(downloader, TransferEvent.DownloadStateChange(info.download.copy(error = null), TransferState.QUEUED)) {
            downloader.clearError(info.download.id).get()
        }
    }

    @Test
    fun `clearError should queue the upload for processing if network is available`() {
        val downloader = newDownloader(true)
        val info = testClearError(downloader)

        downloadOperations.assertDownloadCalled(info.download, info.file)
    }
}