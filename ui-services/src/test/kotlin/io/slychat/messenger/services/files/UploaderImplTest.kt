package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.http.api.upload.NewUploadError
import io.slychat.messenger.core.http.api.upload.NewUploadResponse
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomQuota
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.*
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.TestScheduler
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UploaderImplTest {
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

    private val simulUploads = 10
    private val uploadPersistenceManager: UploadPersistenceManager = mock()
    private val scheduler = TestScheduler()
    private val uploadOperations = MockUploadOperations(scheduler)

    @Before
    fun before() {
        whenever(uploadPersistenceManager.getAll()).thenResolve(emptyList())
        whenever(uploadPersistenceManager.add(any())).thenResolveUnit()
        whenever(uploadPersistenceManager.completePart(any(), any())).thenResolveUnit()
        whenever(uploadPersistenceManager.setState(any(), any())).thenResolveUnit()
        whenever(uploadPersistenceManager.setError(any(), any())).thenResolveUnit()
        whenever(uploadPersistenceManager.remove(any())).thenResolveUnit()
    }

    private fun newUploader(isNetworkAvailable: Boolean = true): UploaderImpl {
        return UploaderImpl(
            simulUploads,
            uploadPersistenceManager,
            uploadOperations,
            scheduler,
            scheduler,
            isNetworkAvailable
        )
    }

    private fun randomMultipartUpload(isPart1Complete: Boolean, isPart2Complete: Boolean): UploadInfo {
        val file = randomRemoteFile()

        val upload = randomUpload(file.id, file.remoteFileSize, UploadState.CREATED).copy(
            parts = listOf(
                UploadPart(1, 0, 1, 1, isPart1Complete),
                UploadPart(2, 1, file.remoteFileSize - 1, file.remoteFileSize - 1, isPart2Complete)
            )
        )

        return UploadInfo(upload, file)
    }

    private fun newUploaderWithUpload(info: UploadInfo, isNetworkAvailable: Boolean = true): UploaderImpl {
        val uploader = newUploader(isNetworkAvailable = isNetworkAvailable)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        uploader.init()

        return uploader
    }

    private fun randomUploadInfo(state: UploadState = UploadState.PENDING, error: UploadError? = null): UploadInfo {
        val file = randomRemoteFile()

        val upload = randomUpload(file.id, file.remoteFileSize, state, error)

        return UploadInfo(upload, file)
    }

    private fun <R> assertEventEmitted(uploader: Uploader, event: TransferEvent, body: () -> R): R {
        val testSubscriber = uploader.events.testSubscriber()

        val r = body()

        assertThat(testSubscriber.onNextEvents).apply {
            describedAs("Should emit ${event.javaClass.simpleName}")
            contains(event)
        }

        return r
    }

    private fun testClearError(uploader: Uploader): UploadInfo {
        val info = randomUploadInfo(error = UploadError.FILE_DISAPPEARED)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        uploader.init()

        uploader.clearError(info.upload.id).get()

        return info.copy(
            upload = info.upload.copy(error = null)
        )
    }

    private fun assertCurrentStates(uploader: UploaderImpl, uploadId: String, transferState: TransferState, uploadState: UploadState) {
        val status = assertNotNull(uploader.get(uploadId), "No such upload")
        assertEquals(transferState, status.state, "Invalid transfer state")
        assertEquals(uploadState, status.upload.state, "Invalid upload state")
    }

    private fun assertStatesUpdated(uploader: UploaderImpl, uploadId: String, transferState: TransferState, uploadState: UploadState) {
        verify(uploadPersistenceManager, description("Upload state not persisted")).setState(uploadId, uploadState)
        assertCurrentStates(uploader, uploadId, transferState, uploadState)
    }

    private fun assertCancelledState(uploader: UploaderImpl, uploadId: String) {
        assertStatesUpdated(uploader, uploadId, TransferState.CANCELLED, UploadState.CANCELLED)
    }

    private fun assertCancellingState(uploader: UploaderImpl, uploadId: String) {
        assertStatesUpdated(uploader, uploadId, TransferState.CANCELLING, UploadState.CANCELLING)
    }

    @Test
    fun `it should fetch uploads from storage on init`() {
        val uploadInfo = listOf(randomUploadInfo(), randomUploadInfo())

        whenever(uploadPersistenceManager.getAll()).thenResolve(uploadInfo)

        val uploader = newUploader(false)

        uploader.init()

        val actual = uploader.uploads.map { UploadInfo(it.upload, it.file) }

        assertThat(actual).apply {
            describedAs("Should contain initial uploads")
            containsAll(uploadInfo)
        }
    }

    @Test
    fun `it should start fetched uploads with state=PENDING when network is available`() {
        val info = randomUploadInfo(state = UploadState.PENDING)

        val uploader = newUploader(true)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        uploader.init()

        uploadOperations.assertCreateCalled(info.upload, info.file)
    }

    @Test
    fun `it should start fetched uploads with state=CREATED when network is available`() {
        val info = randomUploadInfo(state = UploadState.CREATED)

        val uploader = newUploader(true)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        uploader.init()

        uploadOperations.assertUploadPartCalled(info.upload, info.upload.parts[0], info.file)
    }

    @Test
    fun `it should start fetched uploads with state=CANCELLING when network is available`() {
        val info = randomUploadInfo(state = UploadState.CANCELLING)

        val uploader = newUploaderWithUpload(info)

        uploadOperations.assertCancelCalled(info.upload.id)
    }

    @Test
    fun `it should not start fetched uploads which have an associated error`() {
        val file = randomRemoteFile()
        val upload = randomUpload(file.id, file.remoteFileSize).copy(
            error = UploadError.INSUFFICIENT_QUOTA
        )

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(UploadInfo(upload, file)))

        val uploader = newUploader(true)

        uploader.init()

        uploadOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should emit UploadAdded with state=ERROR when fetching uploads with an associated error`() {
        val file = randomRemoteFile()
        val upload = randomUpload(file.id, file.remoteFileSize).copy(
            error = UploadError.INSUFFICIENT_QUOTA
        )

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(UploadInfo(upload, file)))

        val uploader = newUploader(true)

        assertEventEmitted(uploader, TransferEvent.Added(upload, TransferState.ERROR)) {
            uploader.init()
        }
    }

    @Test
    fun `it should not start fetched complete uploads`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        val uploader = newUploader(true)

        uploader.init()

        uploadOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should emit UploadAdded with state=COMPLETE when fetching a completed upload`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        val uploader = newUploader(true)

        assertEventEmitted(uploader, TransferEvent.Added(info.upload, TransferState.COMPLETE)) {
            uploader.init()
        }
    }

    @Test
    fun `adding a new upload should persist the upload info`() {
        val uploader = newUploader(false)

        val uploadInfo = randomUploadInfo()

        uploader.upload(uploadInfo).get()

        verify(uploadPersistenceManager).add(uploadInfo)
    }

    @Test
    fun `adding a new upload should emit an UploadAdded event`() {
        val uploader = newUploader(false)

        val uploadInfo = randomUploadInfo()

        val event = TransferEvent.Added(uploadInfo.upload, TransferState.QUEUED)
        assertEventEmitted(uploader, event) {
            uploader.upload(uploadInfo).get()
        }
    }

    @Test
    fun `it should not attempt to start uploads when network is unavailable`() {
        val uploader = newUploader(false)

        val uploadInfo = randomUploadInfo()

        uploader.upload(uploadInfo).get()

        uploadOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should immediately start an upload if queue space is available and the network is available`() {
        val uploader = newUploader()

        val uploadInfo = randomUploadInfo()

        uploader.upload(uploadInfo).get()

        uploadOperations.assertCreateCalled(uploadInfo.upload, uploadInfo.file)
    }

    @Test
    fun `it should start queued uploads when network becomes available`() {
        val uploader = newUploader(false)

        val uploadInfo = randomUploadInfo()

        uploader.upload(uploadInfo).get()

        uploader.isNetworkAvailable = true

        uploadOperations.assertCreateCalled(uploadInfo.upload, uploadInfo.file)
    }

    @Test
    fun `it should update upload state when creation succeeds`() {
        val uploader = newUploader()

        val uploadInfo = randomUploadInfo()

        uploadOperations.autoResolveCreate = true

        uploader.upload(uploadInfo).get()

        verify(uploadPersistenceManager).setState(uploadInfo.upload.id, UploadState.CREATED)
    }

    @Test
    fun `creation should move to caching state if isEncrypted is true`() {
        val file = randomRemoteFile()

        val upload = randomUpload(file.id, file.remoteFileSize, UploadState.PENDING).copy(
            isEncrypted = true,
            cachePath = "/path"
        )

        val info = UploadInfo(upload, file)

        uploadOperations.autoResolveCreate = true

        val uploader = newUploaderWithUpload(info)

        verify(uploadPersistenceManager).setState(info.upload.id, UploadState.CACHING)
    }

    @Test
    fun `creation should emit quota info when not enough quota is available`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        val quota = randomQuota()

        val testSubscriber = uploader.quota.testSubscriber()

        uploadOperations.resolveCreateOperation(info.upload.id, NewUploadResponse(NewUploadError.INSUFFICIENT_QUOTA, quota))

        testSubscriber.assertReceivedOnNext(listOf(quota))
    }

    @Test
    fun `creation should emit quota info when creation was successful`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        val quota = randomQuota()

        val testSubscriber = uploader.quota.testSubscriber()

        uploadOperations.resolveCreateOperation(info.upload.id, NewUploadResponse(null, quota))

        testSubscriber.assertReceivedOnNext(listOf(quota))
    }

    @Test
    fun `it should move to created state once caching completes`() {
        val file = randomRemoteFile()

        val upload = randomUpload(file.id, file.remoteFileSize, UploadState.CACHING).copy(
            isEncrypted = true,
            cachePath = "/path"
        )

        val info = UploadInfo(upload, file)

        uploadOperations.autoResolveCreate = true

        val uploader = newUploaderWithUpload(info)

        uploadOperations.completeCacheOperation(upload.id)

        verify(uploadPersistenceManager).setState(info.upload.id, UploadState.CREATED)
    }

    @Test
    fun `it should upload parts once upload has been created`() {
        val uploader = newUploader()

        val uploadInfo = randomUploadInfo()

        uploadOperations.autoResolveCreate = true

        uploader.upload(uploadInfo).get()

        val upload = uploadInfo.upload
        uploadOperations.assertUploadPartCalled(upload.copy(state = UploadState.CREATED), upload.parts[0], uploadInfo.file)
    }

    @Test
    fun `it should move an upload to completion state once all parts have been uploaded for a single part upload`() {
        val uploader = newUploader()

        val uploadInfo = randomUploadInfo()

        uploadOperations.autoResolveCreate = true

        uploader.upload(uploadInfo).get()

        uploadOperations.completeUploadPartOperation(1)

        verify(uploadPersistenceManager).setState(uploadInfo.upload.id, UploadState.COMPLETE)

        uploadOperations.assertCompleteNotCalled()
    }

    private fun runMultipartCompletion(): Upload {
        val uploader = newUploader()

        val file = randomRemoteFile()

        val upload = randomUpload(file.id, file.remoteFileSize, UploadState.CREATED).copy(
            parts = listOf(
                UploadPart(1, 0, 1, 1, true),
                UploadPart(2, 1, file.remoteFileSize - 1, file.remoteFileSize - 1, false)
            )
        )

        val info = UploadInfo(upload, file)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        uploader.init()

        uploadOperations.completeUploadPartOperation(2)

        return upload.copy(
            parts = listOf(
                upload.parts[0],
                upload.parts[1].copy(isComplete = true)
            )
        )
    }

    @Test
    fun `it should complete an upload remotely if multipart`() {
        val expected = runMultipartCompletion()
        uploadOperations.assertCompleteCalled(expected)
    }

    @Test
    fun `it should update state to complete once multipart completion occurs`() {
        val expected = runMultipartCompletion()
        uploadOperations.completeCompleteUploadOperation()
        verify(uploadPersistenceManager).setState(expected.id, UploadState.COMPLETE)
    }

    @Test
    fun `it should move an upload to error state if multipart completion fails`() {
        val expected = runMultipartCompletion()
        uploadOperations.rejectCompleteUploadOperation(TestException())
        verify(uploadPersistenceManager).setError(expected.id, UploadError.UNKNOWN)
    }

    @Test
    fun `it should emit a TransferEvent when moving an upload to completion state`() {
        val uploader = newUploader()

        val uploadInfo = randomUploadInfo()

        uploadOperations.autoResolveCreate = true

        val upload = uploadInfo.upload
        val updated = upload.markPartCompleted(1).copy(
            state = UploadState.COMPLETE
        )

        assertEventEmitted(uploader, TransferEvent.StateChanged(updated, TransferState.COMPLETE)) {
            uploader.upload(uploadInfo).get()

            uploadOperations.completeUploadPartOperation(1)
        }
    }

    @Test
    fun `it should mark the upload as error when an exception occurs during creation`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.rejectCreateOperation(info.upload.id, TestException())

        val status = assertNotNull(uploader.uploads.find { it.upload.id == info.upload.id }, "Upload not found in list")

        assertEquals(TransferState.ERROR, status.state, "Invalid state")
    }

    @Test
    fun `it should update upload error when creation fails`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.rejectCreateOperation(info.upload.id, TestException())

        verify(uploadPersistenceManager).setError(info.upload.id, UploadError.UNKNOWN)
    }

    @Test
    fun `it should not attempt to upload parts when creation fails`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.rejectCreateOperation(info.upload.id, TestException())

        uploadOperations.assertUploadPartNotCalled()
    }

    @Test
    fun `it should emit a TransferEvent when an exception occurs during creation`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        val updated = info.upload.copy(error = UploadError.UNKNOWN)
        assertEventEmitted(uploader, TransferEvent.StateChanged(updated, TransferState.ERROR)) {
            uploadOperations.rejectCreateOperation(info.upload.id, TestException())
        }
    }

    @Test
    fun `it should set upload error to INSUFFICIENT_QUOTA when creation returns with insufficent quota`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.resolveCreateOperation(info.upload.id, NewUploadResponse(NewUploadError.INSUFFICIENT_QUOTA, randomQuota()))

        val status = assertNotNull(uploader.uploads.find { it.upload.id == info.upload.id }, "Upload not found in list")

        assertEquals(UploadError.INSUFFICIENT_QUOTA, status.upload.error, "Invalid error")
    }

    @Test
    fun `clearError should clear the stored update error`() {
        val uploader = newUploader(true)

        val info = testClearError(uploader)
        verify(uploadPersistenceManager).setError(info.upload.id, null)
    }

    @Test
    fun `clearError should reset the cached upload's error state`() {
        val uploader = newUploader(true)

        val info = testClearError(uploader)

        val status = assertNotNull(uploader.uploads.find { it.upload.id == info.upload.id }, "Upload not found")

        assertNull(status.upload.error, "Error not cleared")
    }

    @Test
    fun `clearError should update emit a TransferEvent on clear`() {
        val uploader = newUploader(true)
        val info = randomUploadInfo(error = UploadError.FILE_DISAPPEARED)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        uploader.init()

        assertEventEmitted(uploader, TransferEvent.StateChanged(info.upload.copy(error = null), TransferState.QUEUED)) {
            uploader.clearError(info.upload.id).get()
        }
    }

    @Test
    fun `clearError should queue the upload for processing if network is available`() {
        val uploader = newUploader(true)
        val info = testClearError(uploader)

        uploadOperations.assertCreateCalled(info.upload, info.file)
    }

    @Test
    fun `it should emit progress events when uploads reports progress`() {
        val info = randomUploadInfo(state = UploadState.CREATED)
        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        val uploader = newUploader()

        uploader.init()

        val uploadId = info.upload.id
        uploadOperations.sendUploadProgress(uploadId, 1, 500L)
        uploadOperations.sendUploadProgress(uploadId, 1, 500L)

        val partProgress = info.upload.parts.map { UploadPartTransferProgress(1000, it.remoteSize) }

        val progress = UploadTransferProgress(
            partProgress,
            1000,
            info.file.remoteFileSize
        )

        val event = TransferEvent.Progress(info.upload, progress)
        assertEventEmitted(uploader, event) {
            scheduler.advanceTimeBy(DownloaderImpl.PROGRESS_TIME_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `remove should throw IllegalStateException if called for an active upload`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        assertFailsWith(IllegalStateException::class) {
            uploader.remove(listOf(info.upload.id)).get()
        }
    }

    @Test
    fun `remove should throw InvalidUploadException if called for an invalid upload id`() {
        val uploader = newUploader()

        assertFailsWith(InvalidUploadException::class) {
            uploader.remove(listOf(generateUploadId())).get()
        }
    }

    @Test
    fun `remove should remove a non-active upload`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)

        val uploader = newUploaderWithUpload(info)

        uploader.remove(listOf(info.upload.id)).get()

        verify(uploadPersistenceManager).remove(listOf(info.upload.id))

        assertThat(uploader.uploads.map { it.upload }).apply {
            describedAs("Should remove uploads from list")
            doesNotContain(info.upload)
        }
    }

    @Test
    fun `remove should emit an UploadRemoved event`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)

        val uploader = newUploaderWithUpload(info)

        val ev = TransferEvent.Removed(listOf(Transfer.U(info.upload)))

        assertEventEmitted(uploader, ev) {
            uploader.remove(listOf(info.upload.id)).get()
        }
    }

    //not yet pushed remotely
    @Test
    fun `cancel should move a queued pending upload to cancelled state`() {
        val info = randomUploadInfo(state = UploadState.PENDING)

        val uploader = newUploaderWithUpload(info, false)

        val uploadId = info.upload.id
        uploader.cancel(uploadId)

        assertCancelledState(uploader, uploadId)
    }

    //if we've already sent a request over, wait until it finishes and then cancel it
    @Test
    fun `cancel should not modify upload state for an active PENDING upload until its operation completes`() {
        val info = randomUploadInfo(state = UploadState.PENDING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        assertCurrentStates(uploader, uploadId, TransferState.ACTIVE, UploadState.PENDING)
    }

    @Test
    fun `cancel should move an active PENDING upload to cancelling state after its operation completes`() {
        val info = randomUploadInfo(state = UploadState.PENDING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        uploadOperations.resolveCreateOperation(uploadId, NewUploadResponse(null, randomQuota()))

        assertCancellingState(uploader, uploadId)
    }

    @Test
    fun `cancel should not modify upload state for an active CACHING upload until its operation completes`() {
        val info = randomUploadInfo(state = UploadState.CACHING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        assertCurrentStates(uploader, uploadId, TransferState.ACTIVE, UploadState.CACHING)
    }

    @Test
    fun `cancel should move an active CACHING upload to cancelling state after its operation completes`() {
        val info = randomUploadInfo(state = UploadState.CACHING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        uploadOperations.completeCacheOperation(uploadId)

        assertCancellingState(uploader, uploadId)
    }

    @Test
    fun `cancel should attempt to cancel an ongoing transfer`() {
        val info = randomUploadInfo(state = UploadState.CREATED)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        uploadOperations.assertUploadPartCancelled(uploadId, 1)
    }

    @Test
    fun `it should move a cancelled ongoing transfer to CANCELLING state`() {
        val info = randomUploadInfo(state = UploadState.CREATED)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)

        uploadOperations.errorUploadPartOperation(1, CancellationException())

        assertCancellingState(uploader, uploadId)
    }

    @Test
    fun `it should keep processing a cancelled ongoing transfer and cancel it remotely`() {
        val info = randomUploadInfo(state = UploadState.CREATED)

        val uploader = newUploaderWithUpload(info, true)

        uploadOperations.errorUploadPartOperation(1, CancellationException())

        uploadOperations.assertCancelCalled(info.upload.id)
    }

    //this also works if in between the last part and completing the upload
    @Test
    fun `cancel should cause cancellation if called before a transfer begins its next part`() {
        val info = randomMultipartUpload(false, false)

        //we need to get the cancel in before this completes
        val d = deferred<Unit, Exception>()
        whenever(uploadPersistenceManager.completePart(any(), any())).thenReturn(d.promise)

        val uploader = newUploaderWithUpload(info)

        uploadOperations.completeUploadPartOperation(1)

        //here the cancellation token is null, since the transfer's done and we're just persisting the upload state
        uploader.cancel(info.upload.id)

        d.resolve(Unit)

        assertCancellingState(uploader, info.upload.id)
    }

    @Test
    fun `cancel should do nothing if an active upload completes before cancellation can occur`() {
        val info = randomMultipartUpload(true, true)

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(info.upload.id)

        uploadOperations.completeCompleteUploadOperation()

        assertStatesUpdated(uploader, info.upload.id, TransferState.COMPLETE, UploadState.COMPLETE)
    }

    @Test
    fun `cancel should move a non-PENDING upload in error state to CANCELLING state`() {
        val info = randomUploadInfo(state = UploadState.CREATED, error = UploadError.FILE_DISAPPEARED)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        assertCancellingState(uploader, uploadId)
    }
    
    @Test
    fun `cancel should clear any error for an upload in error state`() {
        val info = randomUploadInfo(state = UploadState.CREATED, error = UploadError.FILE_DISAPPEARED)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        verify(uploadPersistenceManager).setError(info.upload.id, null)
        assertNull(assertNotNull(uploader.get(uploadId)).upload.error, "Cached error not cleared")
    }

    @Test
    fun `cancel should move a non-PENDING upload in ERROR state back to the queue`() {
        val info = randomUploadInfo(state = UploadState.CREATED, error = UploadError.FILE_DISAPPEARED)

        val uploader = newUploaderWithUpload(info)

        val uploadId = info.upload.id

        uploader.cancel(uploadId)

        uploadOperations.assertCancelCalled(uploadId)
    }

    @Test
    fun `cancel should move a PENDING upload in error state to CANCELLED state`() {
        val info = randomUploadInfo(state = UploadState.PENDING, error = UploadError.FILE_DISAPPEARED)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        assertCancelledState(uploader, uploadId)
    }

    @Test
    fun `cancel should do nothing for an already cancelled upload`() {
        val info = randomUploadInfo(state = UploadState.CANCELLED)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        verify(uploadPersistenceManager, never()).setState(uploadId, UploadState.CANCELLING)
    }

    @Test
    fun `cancel should do nothing for an inactive completed upload`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)
        uploader.cancel(uploadId)

        verify(uploadPersistenceManager, never()).setState(uploadId, UploadState.CANCELLING)
    }

    @Test
    fun `cancel should do nothing for an upload in CANCELLING STATE`() {
        val info = randomUploadInfo(state = UploadState.CANCELLING)

        val uploader = newUploaderWithUpload(info)

        val uploadId = info.upload.id

        uploader.cancel(uploadId)

        verify(uploadPersistenceManager, never()).setState(any(), any())
    }

    @Test
    fun `it should attempt to delete an upload remotely if an upload is in cancelling state`() {
        val info = randomUploadInfo(state = UploadState.CANCELLING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info)

        uploadOperations.assertCancelCalled(uploadId)
    }

    @Test
    fun `remove should remove a cancelled upload`() {
        val info = randomUploadInfo(UploadState.CANCELLING)

        val uploader = newUploaderWithUpload(info)

        uploadOperations.resolveCancelOperation(info.upload.id)

        uploader.remove(listOf(info.upload.id)).get()

        assertNull(uploader.uploads.find { it.upload.id == info.upload.id }, "Upload not removed from list")
    }

    @Test
    fun `it should move an upload from cancelling to cancelled state once cancellation process completes`() {
        val info = randomUploadInfo(state = UploadState.CANCELLING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info)

        uploadOperations.resolveCancelOperation(uploadId)

        assertCancelledState(uploader, uploadId)
    }

    @Test
    fun `it should move an upload from cancelling to error if an error occurs during the cancellation process`() {
        val info = randomUploadInfo(state = UploadState.CANCELLING)
        val uploadId = info.upload.id

        val uploader = newUploaderWithUpload(info, true)

        uploadOperations.rejectCancelOperation(uploadId, TestException())

        assertEquals(TransferState.ERROR, assertNotNull(uploader.get(uploadId)).state, "Upload not moved to ERROR state")
    }
}