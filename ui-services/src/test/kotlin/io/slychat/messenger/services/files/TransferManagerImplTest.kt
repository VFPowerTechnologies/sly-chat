package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.persistence.DownloadError
import io.slychat.messenger.core.persistence.UploadError
import io.slychat.messenger.core.persistence.UploadState
import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.testutils.desc
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.verification.VerificationMode
import rx.schedulers.TestScheduler
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransferManagerImplTest {
    private val userConfigService = UserConfigService(DummyConfigBackend())
    private val uploader: Uploader = mock()
    private val downloader: Downloader = mock()
    private val uploadTransferEvents = PublishSubject.create<TransferEvent>()
    private val downloadTransferEvents = PublishSubject.create<TransferEvent>()
    private val networkStatus: PublishSubject<Boolean> = PublishSubject.create()
    private val scheduler = TestScheduler()

    private fun newManager(autoRemoveCompletedTransfers: Boolean = false): TransferManagerImpl {
        userConfigService.withEditor {
            transfersSimulDownloads = 1
            transfersSimulUploads = 2
        }

        return TransferManagerImpl(
            userConfigService,
            uploader,
            downloader,
            scheduler,
            scheduler,
            networkStatus,
            autoRemoveCompletedTransfers
        )
    }

    @Before
    fun before() {
        whenever(downloader.events).thenReturn(downloadTransferEvents)
        whenever(uploader.events).thenReturn(uploadTransferEvents)
        whenever(downloader.clearError(any())).thenResolveUnit()
        whenever(uploader.clearError(any())).thenResolveUnit()
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

    private fun getEventSubject(transfer: Transfer): PublishSubject<TransferEvent> {
        return when (transfer) {
            is Transfer.U -> uploadTransferEvents
            is Transfer.D -> downloadTransferEvents
        }
    }

    private fun assertErrorNeverCleared(transfer: Transfer) {
        when (transfer) {
            is Transfer.U -> verify(uploader, never()).clearError(transfer.id)
            is Transfer.D -> verify(downloader, never()).clearError(transfer.id)
        }
    }

    private fun testRetryErrorTransfer(transfer: Transfer, ev: TransferEvent, times: VerificationMode) {
        val manager = newManager()

        val subject = getEventSubject(transfer)

        subject.onNext(ev)

        //FIXME
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        when (transfer) {
            is Transfer.U -> verify(uploader, times).clearError(transfer.id)
            is Transfer.D -> verify(downloader, times).clearError(transfer.id)
        }
    }

    @Test
    fun `it should attempt to resume added transfers with transient errors (upload)`() {
        val transfer = Transfer.U(randomUpload(error = UploadError.NETWORK_ISSUE))
        testRetryErrorTransfer(transfer, TransferEvent.Added(transfer, TransferState.ERROR), times(1))
    }

    @Test
    fun `it should attempt to resume added transfers with transient errors (download)`() {
        val transfer = Transfer.D(randomDownload(error = DownloadError.NETWORK_ISSUE))
        testRetryErrorTransfer(transfer, TransferEvent.Added(transfer, TransferState.ERROR), times(1))
    }

    @Test
    fun `it should attempt to resume updated transfers with transient errors (upload)`() {
        val transfer = Transfer.U(randomUpload(error = UploadError.NETWORK_ISSUE))
        testRetryErrorTransfer(transfer, TransferEvent.StateChanged(transfer, TransferState.ERROR), times(1))
    }

    @Test
    fun `it should attempt to resume updated transfers with transient errors (download)`() {
        val transfer = Transfer.D(randomDownload(error = DownloadError.NETWORK_ISSUE))
        testRetryErrorTransfer(transfer, TransferEvent.StateChanged(transfer, TransferState.ERROR), times(1))
    }

    @Test
    fun `it should ignore added transfers with non-transient errors (upload)`() {
        val transfer = Transfer.U(randomUpload(error = UploadError.FILE_DISAPPEARED))
        testRetryErrorTransfer(transfer, TransferEvent.Added(transfer, TransferState.ERROR), never())
    }

    @Test
    fun `it should ignore added transfers with non-transient errors (download)`() {
        val transfer = Transfer.D(randomDownload(error = DownloadError.NO_SPACE))
        testRetryErrorTransfer(transfer, TransferEvent.Added(transfer, TransferState.ERROR), never())
    }
    
    @Test
    fun `it should ignore updated transfers with transient errors (upload)`() {
        val transfer = Transfer.U(randomUpload(error = UploadError.FILE_DISAPPEARED))
        testRetryErrorTransfer(transfer, TransferEvent.StateChanged(transfer, TransferState.ERROR), never())
    }
    
    @Test
    fun `it should ignore updated transfers with transient errors (download)`() {
        val transfer = Transfer.D(randomDownload(error = DownloadError.NO_SPACE))
        testRetryErrorTransfer(transfer, TransferEvent.StateChanged(transfer, TransferState.ERROR), never())
    }

    @Test
    fun `it should increase timeout on consecutive failures`() {
        val transfer = Transfer.D(randomDownload(error = DownloadError.NETWORK_ISSUE))

        val manager = newManager()

        val subject = getEventSubject(transfer)

        val ev = TransferEvent.StateChanged(transfer, TransferState.ERROR)

        (0..1).forEach {
            //this is kinda hacky but so long as the number's <= 2^2 it's fine
            subject.onNext(ev)
            scheduler.advanceTimeBy(30, TimeUnit.SECONDS)
        }

        verify(downloader, times(2)).clearError(transfer.id)
    }

    private fun testTriggerRetry(transfer: Transfer): TransferManagerImpl {
        val manager = newManager()

        val subject = getEventSubject(transfer)

        val ev = TransferEvent.StateChanged(transfer, TransferState.ERROR)

        subject.onNext(ev)
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        return manager
    }

    //TODO test when error doesn't stay null
    @Test
    fun `it should clear the retry timer when progress is received`() {
        val transfer = Transfer.D(randomDownload(error = DownloadError.NETWORK_ISSUE))

        val manager = testTriggerRetry(transfer)

        val subject = getEventSubject(transfer)
        subject.onNext(TransferEvent.Progress(transfer.download.copy(error = null), DownloadTransferProgress(1, 10)))

        assertNull(manager.retryAttemptCounters[transfer.id], "Retry count not cleared")
    }

    @Test
    fun `it should clear the retry timer when the underlying transfer's state changes`() {
        val transfer = Transfer.U(randomUpload(error = UploadError.NETWORK_ISSUE))

        val manager = testTriggerRetry(transfer)

        val subject = getEventSubject(transfer)
        subject.onNext(TransferEvent.StateChanged(transfer.upload.copy(error = null, state = UploadState.CREATED), TransferState.ACTIVE))

        assertNull(manager.retryAttemptCounters[transfer.id], "Retry count not cleared")
    }

    @Test
    fun `it should not clear the retry timer when the underlying transfer's state doesn't change`() {
        val transfer = Transfer.U(randomUpload(error = UploadError.NETWORK_ISSUE))

        val manager = testTriggerRetry(transfer)

        val subject = getEventSubject(transfer)
        subject.onNext(TransferEvent.StateChanged(transfer.upload.copy(error = null), TransferState.ACTIVE))

        assertNotNull(manager.retryAttemptCounters[transfer.id], "Retry count cleared")
    }

    @Test
    fun `it should emit UntilRetry events while the retry timer is active`() {
        val manager = newManager()

        val transfer = Transfer.U(randomUpload(error = UploadError.NETWORK_ISSUE))

        val subject = getEventSubject(transfer)

        subject.onNext(TransferEvent.Added(transfer, TransferState.ERROR))

        val testSubscriber = manager.events.ofType(TransferEvent.UntilRetry::class.java).testSubscriber()

        //FIXME
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        val events = testSubscriber.onNextEvents
        assertThat(events).desc("Should emit retry timer events") {
            isNotEmpty()
        }
    }

    @Test
    fun `it should emit a UntilRetry with inSeconds=0 when timer expires`() {
        val manager = newManager()

        val transfer = Transfer.U(randomUpload(error = UploadError.NETWORK_ISSUE))

        val subject = getEventSubject(transfer)

        subject.onNext(TransferEvent.Added(transfer, TransferState.ERROR))

        val testSubscriber = manager.events.ofType(TransferEvent.UntilRetry::class.java).testSubscriber()

        //FIXME
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        assertEquals(0, testSubscriber.onNextEvents.last().remainingSecs, "Final time is incorrect")
    }

    //eg: from the ui
    @Test
    fun `it should remove a pending retry if its error is cleared`() {
        val upload = randomUpload(error = UploadError.NETWORK_ISSUE)
        val transfer = Transfer.U(upload)

        val manager = newManager()

        val subject = getEventSubject(transfer)

        subject.onNext(TransferEvent.Added(transfer, TransferState.ERROR))

        val updated = Transfer.U(upload.copy(error = null))
        subject.onNext(TransferEvent.StateChanged(updated, TransferState.QUEUED))

        //FIXME
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        assertErrorNeverCleared(transfer)
    }

    @Test
    fun `it should remove a pending retry if its transfer removed`() {
        val upload = randomUpload(error = UploadError.NETWORK_ISSUE)
        val transfer = Transfer.U(upload)

        val manager = newManager()

        val subject = getEventSubject(transfer)

        subject.onNext(TransferEvent.Added(transfer, TransferState.ERROR))

        val updated = Transfer.U(upload.copy(error = null))
        subject.onNext(TransferEvent.Removed(listOf(updated)))

        //FIXME
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        assertErrorNeverCleared(transfer)
    }

    @Test
    fun `it should emit an UntilRetry event if a timer is cancelled`() {
        val upload = randomUpload(error = UploadError.NETWORK_ISSUE)
        val transfer = Transfer.U(upload)

        val manager = newManager()

        val subject = getEventSubject(transfer)

        subject.onNext(TransferEvent.Added(transfer, TransferState.ERROR))

        val testSubscriber = manager.events.ofType(TransferEvent.UntilRetry::class.java).testSubscriber()

        val updated = Transfer.U(upload.copy(error = null))
        subject.onNext(TransferEvent.StateChanged(updated, TransferState.QUEUED))

        //FIXME
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        assertThat(testSubscriber.onNextEvents).desc("Should contain a terminating event") {
            contains(TransferEvent.UntilRetry(updated, 0))
        }
    }

    private fun testAutoRemovalSendEvent(transfer: Transfer, state: TransferState, isStateChange: Boolean) {
        val manager = newManager(autoRemoveCompletedTransfers = true)

        val ev = if (isStateChange)
            TransferEvent.StateChanged(transfer, state)
        else
            TransferEvent.Added(transfer, state)

        when (transfer) {
            is Transfer.U -> uploadTransferEvents.onNext(ev)
            is Transfer.D -> downloadTransferEvents.onNext(ev)
        }
    }

    private fun testAutoRemoval(transfer: Transfer, state: TransferState, isStateChange: Boolean) {
        testAutoRemovalSendEvent(transfer, state, isStateChange)

        val transferIds = listOf(transfer.id)

        when (transfer) {
            is Transfer.U -> verify(uploader).remove(transferIds)
            is Transfer.D -> verify(downloader).remove(transferIds)
        }
    }

    private fun testAutoRemovalIgnore(transfer: Transfer, state: TransferState, isStateChange: Boolean) {
        testAutoRemovalSendEvent(transfer, state, isStateChange)

        when (transfer) {
            is Transfer.U -> verify(uploader, never()).remove(any())
            is Transfer.D -> verify(downloader, never()).remove(any())
        }
    }

    @Test
    fun `it should remove completed uploads when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.U(randomUpload()), TransferState.COMPLETE, true)
    }

    @Test
    fun `it should remove cancelled uploads when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.U(randomUpload()), TransferState.CANCELLED, true)
    }

    @Test
    fun `it should ignore non-completion state changes for uploads when autoRemoveCompletedTransfers is true`() {
        testAutoRemovalIgnore(Transfer.U(randomUpload()), TransferState.ERROR, true)
    }

    @Test
    fun `it should ignore non-completion state changes for downloads when autoRemoveCompletedTransfers is true`() {
        testAutoRemovalIgnore(Transfer.D(randomDownload()), TransferState.ERROR, true)
    }

    @Test
    fun `it should remove completed downloads when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.D(randomDownload()), TransferState.COMPLETE, true)
    }

    @Test
    fun `it should remove cancelled downloads when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.D(randomDownload()), TransferState.CANCELLED, true)
    }

    @Test
    fun `it should remove added uploads in completion state when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.U(randomUpload()), TransferState.COMPLETE, false)
    }

    @Test
    fun `it should remove added uploads in cancellation state when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.U(randomUpload()), TransferState.COMPLETE, false)
    }

    @Test
    fun `it should remove added downloads in completion state when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.D(randomDownload()), TransferState.COMPLETE, false)
    }

    @Test
    fun `it should remove added downloads in cancellation state when autoRemoveCompletedTransfers is true`() {
        testAutoRemoval(Transfer.D(randomDownload()), TransferState.COMPLETE, false)
    }

    @Test
    fun `it should ignore non-completion added uploads when autoRemoveCompletedTransfers is true`() {
        testAutoRemovalIgnore(Transfer.U(randomUpload()), TransferState.ERROR, false)
    }

    @Test
    fun `it should ignore non-completion added downloads when autoRemoveCompletedTransfers is true`() {
        testAutoRemovalIgnore(Transfer.D(randomDownload()), TransferState.ERROR, false)
    }
}