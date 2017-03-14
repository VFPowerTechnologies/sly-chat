package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.TestScheduler
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

    private fun newUploader(isNetworkAvailable: Boolean = true): Uploader {
        return UploaderImpl(
            simulUploads,
            uploadPersistenceManager,
            uploadOperations,
            scheduler,
            scheduler,
            isNetworkAvailable
        )
    }

    private fun newUploaderWithUpload(info: UploadInfo, isNetworkAvailable: Boolean = true): Uploader {
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

        assertEventEmitted(uploader, TransferEvent.UploadAdded(upload, TransferState.ERROR)) {
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

        assertEventEmitted(uploader, TransferEvent.UploadAdded(info.upload, TransferState.COMPLETE)) {
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

        val event = TransferEvent.UploadAdded(uploadInfo.upload, TransferState.QUEUED)
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

        assertEventEmitted(uploader, TransferEvent.UploadStateChanged(updated, TransferState.COMPLETE)) {
            uploader.upload(uploadInfo).get()

            uploadOperations.completeUploadPartOperation(1)
        }
    }

    @Test
    fun `it should mark the upload as error when an exception occurs during creation`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.createDeferred.reject(InsufficientQuotaException())

        val status = assertNotNull(uploader.uploads.find { it.upload.id == info.upload.id }, "Upload not found in list")

        assertEquals(TransferState.ERROR, status.state, "Invalid state")
    }

    @Test
    fun `it should update upload error when creation fails`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.createDeferred.reject(InsufficientQuotaException())

        verify(uploadPersistenceManager).setError(info.upload.id, UploadError.INSUFFICIENT_QUOTA)
    }

    @Test
    fun `it should not attempt to upload parts when creation fails`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.createDeferred.reject(InsufficientQuotaException())

        uploadOperations.assertUploadPartNotCalled()
    }

    @Test
    fun `it should emit a TransferEvent when an exception occurs during creation`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        val updated = info.upload.copy(error = UploadError.INSUFFICIENT_QUOTA)
        assertEventEmitted(uploader, TransferEvent.UploadStateChanged(updated, TransferState.ERROR)) {
            uploadOperations.createDeferred.reject(InsufficientQuotaException())
        }
    }

    @Test
    fun `it should set upload error to INSUFFICIENT_QUOTA when creation fails with InsufficientQuotaException`() {
        val uploader = newUploader()

        val info = randomUploadInfo()

        uploader.upload(info).get()

        uploadOperations.createDeferred.reject(InsufficientQuotaException())

        val status = assertNotNull(uploader.uploads.find { it.upload.id == info.upload.id }, "Upload not found in list")

        assertEquals(status.upload.error, UploadError.INSUFFICIENT_QUOTA, "Invalid error")
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

        assertEventEmitted(uploader, TransferEvent.UploadStateChanged(info.upload.copy(error = null), TransferState.QUEUED)) {
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

        val event = TransferEvent.UploadProgress(info.upload, progress)
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

        val ev = TransferEvent.UploadRemoved(listOf(info.upload))

        assertEventEmitted(uploader, ev) {
            uploader.remove(listOf(info.upload.id)).get()
        }
    }
}