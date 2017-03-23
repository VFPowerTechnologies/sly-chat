package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.testutils.thenResolveUnit
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject

class TransferManagerImplTest {
    private val userConfigService = UserConfigService(DummyConfigBackend())
    private val uploader: Uploader = mock()
    private val downloader: Downloader = mock()
    private val networkStatus: PublishSubject<Boolean> = PublishSubject.create()

    private fun newManager(): TransferManagerImpl {
        userConfigService.withEditor {
            transfersSimulDownloads = 1
            transfersSimulUploads = 2
        }

        return TransferManagerImpl(
            userConfigService,
            uploader,
            downloader,
            networkStatus
        )
    }

    @Before
    fun before() {
        whenever(downloader.remove(any())).thenResolveUnit()
        whenever(uploader.remove(any())).thenResolveUnit()
    }

    @Test
    fun `it should set uploader's simul upload count on init`() {
        val manager = newManager()

        verify(uploader).simulUploads = userConfigService.transfersSimulUploads
    }

    @Test
    fun `it should update uploader's simul upload count when config is updated`() {
        val manager = newManager()

        reset(uploader)

        val v = 5
        userConfigService.withEditor {
            transfersSimulUploads = v
        }

        verify(uploader).simulUploads = v
    }

    @Test
    fun `it should set downloader's simul download count on init`() {
        val manager = newManager()

        verify(downloader).simulDownloads = userConfigService.tranfersSimulDownloads
    }

    @Test
    fun `it should update downloader's simul download count when config is updated`() {
        val manager = newManager()

        reset(downloader)

        val v = 5
        userConfigService.withEditor {
            transfersSimulDownloads = v
        }

        verify(downloader).simulDownloads = v
    }

    @Test
    fun `removeCompleted should remove completed and cancelled downloads`() {
        val completed = randomDownloadStatus(TransferState.COMPLETE)
        val cancelled = randomDownloadStatus(TransferState.CANCELLED)

        val statuses = listOf(
            completed,
            randomDownloadStatus(TransferState.QUEUED),
            randomDownloadStatus(TransferState.ACTIVE),
            cancelled,
            randomDownloadStatus(TransferState.ERROR)
        )

        whenever(downloader.downloads).thenReturn(statuses)

        val manager = newManager()

        manager.removeCompleted().get()

        verify(downloader).remove(listOf(completed.download.id, cancelled.download.id))
    }

    @Test
    fun `removeCompletedUploads should remove completed uploads`() {
        val completed = randomUploadStatus(TransferState.COMPLETE)
        val cancelled = randomUploadStatus(TransferState.CANCELLED)

        val statuses = listOf(
            completed,
            randomUploadStatus(TransferState.QUEUED),
            randomUploadStatus(TransferState.ACTIVE),
            cancelled,
            randomUploadStatus(TransferState.ERROR)
        )

        whenever(uploader.uploads).thenReturn(statuses)

        val manager = newManager()

        manager.removeCompleted().get()

        verify(uploader).remove(listOf(completed.upload.id, cancelled.upload.id))
    }
}