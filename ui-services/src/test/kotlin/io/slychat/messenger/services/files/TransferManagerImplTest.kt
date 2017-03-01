package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransferManagerImplTest {
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

    private val transferOptions = TransferOptions(10, 10)
    private val uploadPersistenceManager: UploadPersistenceManager = mock()
    private val transferOperations = MockTransferOperations()
    private val networkStatus = BehaviorSubject.create<Boolean>()

    @Before
    fun before() {
        whenever(uploadPersistenceManager.getAll()).thenResolve(emptyList())
        whenever(uploadPersistenceManager.add(any())).thenResolveUnit()
        whenever(uploadPersistenceManager.completePart(any(), any())).thenResolveUnit()
        whenever(uploadPersistenceManager.setState(any(), any())).thenResolveUnit()
        whenever(uploadPersistenceManager.setError(any(), any())).thenResolveUnit()
    }

    private fun newManager(isNetworkAvailable: Boolean = true): TransferManagerImpl {
        networkStatus.onNext(isNetworkAvailable)

        return TransferManagerImpl(
            transferOptions,
            uploadPersistenceManager,
            transferOperations,
            networkStatus
        )
    }

    private fun randomUploadInfo(partCount: Int = 1, state: UploadState = UploadState.PENDING, error: UploadError? = null): UploadInfo {
        val file = RemoteFile(
            generateFileId(),
            generateShareKey(),
            0,
            false,
            randomUserMetadata(),
            randomFileMetadata(),
            1,
            2,
            randomLong()
        )

        val upload = randomUpload(file.id, file.remoteFileSize, state, error)

        return UploadInfo(upload, file)
    }

    private fun <R> assertEventEmitted(manager: TransferManager, event: TransferEvent, body: () -> R): R {
        val testSubscriber = manager.events.testSubscriber()

        val r = body()

        assertThat(testSubscriber.onNextEvents).apply {
            describedAs("Should emit ${event.javaClass.simpleName}")
            contains(event)
        }

        return r
    }

    @Test
    fun `it should fetch uploads from storage on init`() {
        val uploadInfo = listOf(randomUploadInfo(), randomUploadInfo())

        whenever(uploadPersistenceManager.getAll()).thenResolve(uploadInfo)

        val manager = newManager(false)

        manager.init()

        val actual = manager.uploads.map { UploadInfo(it.upload, it.file) }

        assertThat(actual).apply {
            describedAs("Should contain initial uploads")
            containsAll(uploadInfo)
        }
    }

    @Test
    fun `it should start fetched uploads with state=PENDING when network is available`() {
        val info = randomUploadInfo(state = UploadState.PENDING)

        val manager = newManager(true)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        manager.init()

        transferOperations.assertCreateCalled(info.upload, info.file)
    }

    @Test
    fun `it should start fetched uploads with state=CREATED when network is available`() {
        val info = randomUploadInfo(state = UploadState.CREATED)

        val manager = newManager(true)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        manager.init()

        transferOperations.assertUploadPartCalled(info.upload, info.upload.parts[0], info.file)
    }

    @Test
    fun `it should not start fetched uploads which have an associated error`() {
        val file = randomRemoteFile()
        val upload = randomUpload(file.id, file.remoteFileSize).copy(
            error = UploadError.INSUFFICIENT_QUOTA
        )

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(UploadInfo(upload, file)))

        val manager = newManager(true)

        manager.init()

        transferOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should emit UploadAdded with state=ERROR when fetching uploads with an associated error`() {
        val file = randomRemoteFile()
        val upload = randomUpload(file.id, file.remoteFileSize).copy(
            error = UploadError.INSUFFICIENT_QUOTA
        )

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(UploadInfo(upload, file)))

        val manager = newManager(true)

        assertEventEmitted(manager, TransferEvent.UploadAdded(upload, UploadTransferState.ERROR)) {
            manager.init()
        }
    }

    @Test
    fun `it should not start fetched complete uploads`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        val manager = newManager(true)

        manager.init()

        transferOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should emit UploadAdded with state=COMPLETE when fetching a completed upload`() {
        val info = randomUploadInfo(state = UploadState.COMPLETE)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        val manager = newManager(true)

        assertEventEmitted(manager, TransferEvent.UploadAdded(info.upload, UploadTransferState.COMPLETE)) {
            manager.init()
        }
    }

    @Test
    fun `adding a new upload should persist the upload info`() {
        val manager = newManager(false)

        val uploadInfo = randomUploadInfo()

        manager.upload(uploadInfo).get()

        verify(uploadPersistenceManager).add(uploadInfo)
    }

    //TODO transfer event tests

    @Test
    fun `adding a new upload should emit an UploadAdded event`() {
        val manager = newManager(false)

        val uploadInfo = randomUploadInfo()

        val event = TransferEvent.UploadAdded(uploadInfo.upload, UploadTransferState.QUEUED)
        assertEventEmitted(manager, event) {
            manager.upload(uploadInfo).get()
        }
    }

    @Test
    fun `it should not attempt to start uploads when network is unavailable`() {
        val manager = newManager(false)

        val uploadInfo = randomUploadInfo()

        manager.upload(uploadInfo).get()

        transferOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should immediately start an upload if queue space is available and the network is available`() {
        val manager = newManager()

        val uploadInfo = randomUploadInfo()

        manager.upload(uploadInfo).get()

        transferOperations.assertCreateCalled(uploadInfo.upload, uploadInfo.file)
    }

    @Test
    fun `it should start queued uploads when network becomes available`() {
        val manager = newManager(false)

        val uploadInfo = randomUploadInfo()

        manager.upload(uploadInfo).get()

        networkStatus.onNext(true)

        transferOperations.assertCreateCalled(uploadInfo.upload, uploadInfo.file)
    }

    @Test
    fun `it should update upload state when creation succeeds`() {
        val manager = newManager()

        val uploadInfo = randomUploadInfo()

        transferOperations.autoResolveCreate = true

        manager.upload(uploadInfo).get()

        verify(uploadPersistenceManager).setState(uploadInfo.upload.id, UploadState.CREATED)
    }

    @Test
    fun `it should upload parts once upload has been created`() {
        val manager = newManager()

        val uploadInfo = randomUploadInfo()

        transferOperations.autoResolveCreate = true

        manager.upload(uploadInfo).get()

        val upload = uploadInfo.upload
        transferOperations.assertUploadPartCalled(upload.copy(state = UploadState.CREATED), upload.parts[0], uploadInfo.file)
    }

    @Test
    fun `it should move an upload to completion state once all parts have been uploaded`() {
        val manager = newManager()

        val uploadInfo = randomUploadInfo()

        transferOperations.autoResolveCreate = true

        manager.upload(uploadInfo).get()

        transferOperations.completeUploadPartOperation(1)

        verify(uploadPersistenceManager).setState(uploadInfo.upload.id, UploadState.COMPLETE)
    }

    @Test
    fun `it should emit a TransferEvent when moving an upload to completion state`() {
        val manager = newManager()

        val uploadInfo = randomUploadInfo()

        transferOperations.autoResolveCreate = true

        val upload = uploadInfo.upload
        val updated = upload.markPartCompleted(1).copy(
            state = UploadState.COMPLETE
        )

        assertEventEmitted(manager, TransferEvent.UploadStateChanged(updated, UploadTransferState.COMPLETE)) {
            manager.upload(uploadInfo).get()

            transferOperations.completeUploadPartOperation(1)
        }
    }

    @Test
    fun `it should mark the upload as error when an exception occurs during creation`() {
        val manager = newManager()

        val info = randomUploadInfo()

        manager.upload(info).get()

        transferOperations.createDeferred.reject(InsufficientQuotaException())

        val status = assertNotNull(manager.uploads.find { it.upload.id == info.upload.id }, "Upload not found in list")

        assertEquals(UploadTransferState.ERROR, status.state, "Invalid state")
    }

    @Test
    fun `it should update upload error when creation fails`() {
        val manager = newManager()

        val info = randomUploadInfo()

        manager.upload(info).get()

        transferOperations.createDeferred.reject(InsufficientQuotaException())

        verify(uploadPersistenceManager).setError(info.upload.id, UploadError.INSUFFICIENT_QUOTA)
    }

    @Test
    fun `it should not attempt to upload parts when creation fails`() {
        val manager = newManager()

        val info = randomUploadInfo()

        manager.upload(info).get()

        transferOperations.createDeferred.reject(InsufficientQuotaException())

        transferOperations.assertUploadPartNotCalled()
    }

    @Test
    fun `it should emit a TransferEvent when an exception occurs during creation`() {
        val manager = newManager()

        val info = randomUploadInfo()

        manager.upload(info).get()

        val updated = info.upload.copy(error = UploadError.INSUFFICIENT_QUOTA)
        assertEventEmitted(manager, TransferEvent.UploadStateChanged(updated, UploadTransferState.ERROR)) {
            transferOperations.createDeferred.reject(InsufficientQuotaException())
        }
    }

    @Test
    fun `it should set upload error to INSUFFICIENT_QUOTA when creation fails with InsufficientQuotaException`() {
        val manager = newManager()

        val info = randomUploadInfo()

        manager.upload(info).get()

        transferOperations.createDeferred.reject(InsufficientQuotaException())

        val status = assertNotNull(manager.uploads.find { it.upload.id == info.upload.id }, "Upload not found in list")

        assertEquals(status.upload.error, UploadError.INSUFFICIENT_QUOTA, "Invalid error")
    }

    private fun testClearError(manager: TransferManager): UploadInfo {
        val info = randomUploadInfo(error = UploadError.FILE_DISAPPEARED)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        manager.init()

        manager.clearError(info.upload.id).get()

        return info
    }

    @Test
    fun `clearError should clear the stored update error`() {
        val manager = newManager(true)

        val info = testClearError(manager)
        verify(uploadPersistenceManager).setError(info.upload.id, null)
    }

    @Test
    fun `clearError should reset the cached upload's error state`() {
        val manager = newManager(true)

        val info = testClearError(manager)

        val status = assertNotNull(manager.uploads.find { it.upload.id == info.upload.id }, "Upload not found")

        assertNull(status.upload.error, "Error not cleared")
    }

    @Test
    fun `clearError should update emit a TransferEvent on clear`() {
        val manager = newManager(true)
        val info = randomUploadInfo(error = UploadError.FILE_DISAPPEARED)

        whenever(uploadPersistenceManager.getAll()).thenResolve(listOf(info))

        manager.init()

        assertEventEmitted(manager, TransferEvent.UploadStateChanged(info.upload.copy(error = null), UploadTransferState.QUEUED)) {
            manager.clearError(info.upload.id).get()
        }
    }

    @Test
    fun `clearError should queue the upload for processing if network is available`() {
        val manager = newManager(true)
        val info = testClearError(manager)

        transferOperations.assertCreateCalled(info.upload, info.file)
    }
}