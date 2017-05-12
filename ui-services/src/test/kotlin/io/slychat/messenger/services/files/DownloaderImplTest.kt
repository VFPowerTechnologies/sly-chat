package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateDownloadId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.TestScheduler
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
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
            MockitoKotlin.registerInstanceCreator { UploadPart(1, 0, 10, 10, false) }
        }
    }

    private val simulDownloads = 10
    private val downloadPersistenceManager: DownloadPersistenceManager = mock()
    private val scheduler = TestScheduler()
    private val downloadOperations = MockDownloadOperations(scheduler)

    private fun newDownloader(isNetworkAvailable: Boolean = true): Downloader {
        return DownloaderImpl(
            simulDownloads,
            downloadPersistenceManager,
            downloadOperations,
            scheduler,
            scheduler,
            isNetworkAvailable
        )
    }

    private fun newDownloaderWithDownload(info: DownloadInfo, isNetworkAvailable: Boolean = true): Downloader {
        val downloader = newDownloader(isNetworkAvailable = isNetworkAvailable)

        whenever(downloadPersistenceManager.getAll()).thenResolve(listOf(info))

        downloader.init()

        return downloader
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

    private fun advanceDownloadProgressBuffer() {
        scheduler.advanceTimeBy(DownloaderImpl.PROGRESS_TIME_MS, TimeUnit.MILLISECONDS)
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

        val info = randomDownloadInfo()
        downloader.download(listOf(info)).get()

        val download = info.download

        val event = TransferEvent.StateChanged(download.copy(error = expectedError), TransferState.ERROR)
        assertEventEmitted(downloader, event) {
            downloadOperations.errorDownload(download.id, e)
        }

        verify(downloadPersistenceManager).setError(download.id, expectedError)
    }

    @Before
    fun before() {
        whenever(downloadPersistenceManager.add(any())).thenResolveUnit()
        whenever(downloadPersistenceManager.getAll()).thenResolve(emptyList())
        whenever(downloadPersistenceManager.setState(any(), any())).thenResolveUnit()
        whenever(downloadPersistenceManager.setError(any(), any())).thenResolveUnit()
        whenever(downloadPersistenceManager.remove(any())).thenResolveUnit()
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
        val event = TransferEvent.Added(downloadInfo.download, TransferState.ERROR)
        assertEventEmitted(downloader, event) {
            downloader.init()
        }
    }

    @Test
    fun `adding a new download should persist the download info`() {
        val downloader = newDownloader(false)

        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        verify(downloadPersistenceManager).add(listOf(info.download))
    }

    @Test
    fun `adding a new download should emit a DownloadAdded event`() {
        val downloader = newDownloader(false)

        val info = randomDownloadInfo()

        val event = TransferEvent.Added(info.download, TransferState.QUEUED)
        assertEventEmitted(downloader, event) {
            downloader.download(listOf(info)).get()
        }
    }

    @Test
    fun `it should start queued downloads when network becomes available`() {
        val downloader = newDownloader(false)

        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        downloader.isNetworkAvailable = true

        downloadOperations.assertDownloadCalled(info.download, info.file)
    }

    @Test
    fun `it should emit a TransferEvent when moving a download to completion state`() {
        val downloader = newDownloader(true)

        val info = randomDownloadInfo()
        downloader.download(listOf(info)).get()

        val download = info.download

        val event = TransferEvent.StateChanged(download.copy(state = DownloadState.COMPLETE), TransferState.COMPLETE)
        assertEventEmitted(downloader, event) {
            downloadOperations.completeDownload(download.id)
        }
    }

    @Test
    fun `it should mark a download as completed when a download completes successfully`() {
        val downloader = newDownloader(true)

        val info = randomDownloadInfo()
        downloader.download(listOf(info)).get()

        downloadOperations.completeDownload(info.download.id)

        verify(downloadPersistenceManager).setState(info.download.id, DownloadState.COMPLETE)
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
    fun `it should set the download state to CANCELLED when download fails with CancellationException`() {
        val downloader = newDownloader(true)

        val info = randomDownloadInfo()
        downloader.download(listOf(info)).get()

        val download = info.download
        downloadOperations.errorDownload(download.id, CancellationException())

        verify(downloadPersistenceManager).setState(info.download.id, DownloadState.CANCELLED)
    }

    @Test
    fun `it should emit a TransferEvent with state=CANCELLED when a download is cancelled`() {
        val downloader = newDownloader(true)

        val info = randomDownloadInfo()
        downloader.download(listOf(info)).get()

        val download = info.download

        val event = TransferEvent.StateChanged(download.copy(state = DownloadState.CANCELLED), TransferState.CANCELLED)
        assertEventEmitted(downloader, event) {
            downloadOperations.errorDownload(download.id, CancellationException())
        }
    }

    @Test
    fun `clearError should do nothing if no error is present`() {
        val info = randomDownloadInfo()

        val downloader = newDownloaderWithDownload(info, false)

        downloader.clearError(info.download.id)

        verify(downloadPersistenceManager, never()).setError(info.download.id, null)
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

        assertEventEmitted(downloader, TransferEvent.StateChanged(info.download.copy(error = null), TransferState.QUEUED)) {
            downloader.clearError(info.download.id).get()
        }
    }

    @Test
    fun `an error should reset the download's transfer progress amount`() {
        val info = randomDownloadInfo()
        val downloader = newDownloaderWithDownload(info)

        downloadOperations.sendDownloadProgress(info.download.id, 1000)
        advanceDownloadProgressBuffer()

        assertEventEmitted(downloader, TransferEvent.Progress(info.download.copy(error = DownloadError.UNKNOWN), DownloadTransferProgress(0, info.file.remoteFileSize))) {
            downloadOperations.errorDownload(info.download.id, TestException())
        }
    }

    @Test
    fun `clearError should queue the upload for processing if network is available`() {
        val downloader = newDownloader(true)
        val info = testClearError(downloader)

        downloadOperations.assertDownloadCalled(info.download, info.file)
    }

    @Test
    fun `it should emit progress events when download reports progress`() {
        val downloader = newDownloader(true)
        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        val downloadId = info.download.id
        downloadOperations.sendDownloadProgress(downloadId, 500L)
        downloadOperations.sendDownloadProgress(downloadId, 500L)

        val event = TransferEvent.Progress(info.download, DownloadTransferProgress(1000, info.file.remoteFileSize))
        assertEventEmitted(downloader, event) {
            advanceDownloadProgressBuffer()
        }
    }

    @Test
    fun `cancel should send cancellation to a running download`() {
        val downloader = newDownloader()
        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        downloader.cancel(info.download.id)

        downloadOperations.assertCancelled(info.download.id)
    }

    @Test
    fun `cancel should delete a partially downloaded file after stopping a running download`() {
        val downloader = newDownloader()
        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        downloader.cancel(info.download.id)

        downloadOperations.errorDownload(info.download.id, CancellationException())

        downloadOperations.assertDeleteCalled(info.download)
    }

    @Test
    fun `cancel should delete a partially downloaded file if in error state`() {
        val info = randomDownloadInfo(error = DownloadError.CORRUPTED)
        val downloader = newDownloaderWithDownload(info)

        downloader.cancel(info.download.id)

        downloadOperations.assertDeleteCalled(info.download)
    }

    @Test
    fun `cancel should delete a partially downloaded file if in queued state`() {
        val info = randomDownloadInfo()
        val downloader = newDownloaderWithDownload(info, false)

        downloader.cancel(info.download.id)

        downloadOperations.assertDeleteCalled(info.download)
    }

    @Test
    fun `cancel should do nothing if download wasn't running`() {
        val downloader = newDownloader(false)
        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        downloader.cancel(info.download.id)
    }

    @Test
    fun `cancel should throw InvalidDownloadException if downloadId is invalid`() {
        val downloader = newDownloader()

        assertFailsWith(InvalidDownloadException::class) {
            downloader.cancel(generateDownloadId())
        }
    }

    @Test
    fun `remove should throw IllegalStateException if called for an active download`() {
        val downloader = newDownloader()

        val info = randomDownloadInfo()

        downloader.download(listOf(info)).get()

        assertFailsWith(IllegalStateException::class) {
            downloader.remove(listOf(info.download.id)).get()
        }
    }

    @Test
    fun `remove should throw InvalidDownloadException if called for an invalid download id`() {
        val downloader = newDownloader()

        assertFailsWith(InvalidDownloadException::class) {
            downloader.remove(listOf(generateDownloadId())).get()
        }
    }

    @Test
    fun `remove should remove a non-active download`() {
        val info = randomDownloadInfo(error = DownloadError.REMOTE_FILE_MISSING)

        val downloader = newDownloaderWithDownload(info)

        downloader.remove(listOf(info.download.id)).get()

        verify(downloadPersistenceManager).remove(listOf(info.download.id))

        assertThat(downloader.downloads.map { it.download }).apply {
            describedAs("Should remove download from list")
            doesNotContain(info.download)
        }
    }

    @Test
    fun `remove should emit a DownloadRemoved event`() {
        val info = randomDownloadInfo(error = DownloadError.REMOTE_FILE_MISSING)

        val downloader = newDownloaderWithDownload(info)

        val ev = TransferEvent.Removed(listOf(Transfer.D(info.download)))

        assertEventEmitted(downloader, ev) {
            downloader.remove(listOf(info.download.id)).get()
        }
    }
}