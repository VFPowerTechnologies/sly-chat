package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.UploadInfo
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.core.persistence.UploadPersistenceManager
import io.slychat.messenger.core.persistence.UploadState
import io.slychat.messenger.core.randomFileMetadata
import io.slychat.messenger.core.randomLong
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
import rx.subjects.BehaviorSubject

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

    private fun randomUploadInfo(partCount: Int = 1): UploadInfo {
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

        val upload = randomUpload(file.id, file.remoteFileSize)

        return UploadInfo(upload, file)
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

        transferOperations.getUploadPartDeferred(1).resolve(Unit)

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

            transferOperations.getUploadPartDeferred(1).resolve(Unit)
        }
    }
}