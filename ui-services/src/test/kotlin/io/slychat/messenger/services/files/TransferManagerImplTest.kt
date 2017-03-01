package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.core.persistence.UploadPersistenceManager
import io.slychat.messenger.core.persistence.UploadState
import io.slychat.messenger.core.randomFileMetadata
import io.slychat.messenger.core.randomLong
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolveUnit
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import java.util.*
import kotlin.test.fail

class MockTransferOperations : TransferOperations {
    var createDeferred = deferred<Unit, Exception>()
    var uploadDeferreds = HashMap<Int, Deferred<Unit, Exception>>()

    var autoResolveCreate = false

    private data class CreateArgs(val upload: Upload, val file: RemoteFile)
    private data class UploadArgs(val upload: Upload, val part: UploadPart, val file: RemoteFile)

    private var createArgs: CreateArgs? = null
    private var uploadArgs = HashMap<Int, UploadArgs>()

    override fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception> {
        if (autoResolveCreate)
            return Promise.of(Unit)

        createArgs = CreateArgs(upload, file)
        return createDeferred.promise
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, progressCallback: (Long) -> Unit): Promise<Unit, Exception> {
        uploadArgs[part.n] = UploadArgs(upload, part, file)

        if (part.n in uploadDeferreds)
            throw RuntimeException("Attempted to upload part ${part.n} twice")

        val d = deferred<Unit, Exception>()
        uploadDeferreds[part.n] = d

        return d.promise
    }

    fun assertCreateNotCalled() {
        if (createArgs != null)
            fail("create() called with args=$createArgs")
    }

    fun assertCreateCalled(upload: Upload, file: RemoteFile) {
        val args = createArgs ?: fail("create() not called")

        val expected = CreateArgs(upload, file)
        if (args != expected)
            fail("create() called with differing args\nExpected:\n$expected\n\nActual:\n$args")
    }

    fun assertUploadPartNotCalled(n: Int) {
        val args = uploadArgs[n]
        if (args != null)
            fail("uploadPart() called with args=$args")
    }

    private fun failWithDiff(fnName: String, expected: Any, actual: Any) {
        fail("$fnName() called with differing args\nExpected:\n$expected\n\nActual:\n$actual")
    }

    fun assertUploadPartCalled(upload: Upload, part: UploadPart, file: RemoteFile) {
        val args = uploadArgs[part.n] ?: fail("uploadPart() not called for part ${part.n}")

        if (args.upload != upload)
            failWithDiff("uploadPart", upload, args.upload)
        if (args.part != part)
            failWithDiff("uploadPart", part, args.part)
        if (args.file != file)
            failWithDiff("uploadPart", file, args.file)
    }

    fun getUploadPartDeferred(n: Int): Deferred<Unit, Exception> {
        return uploadDeferreds[n] ?: fail("uploadPart() not called for part $n")
    }
}

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

    private fun randomUploadRequest(partCount: Int = 1): UploadRequest {
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

        return UploadRequest(upload, file)
    }

    //XXX can't do this unless we merge upload/file persistence; else we need to get all uploads and then fetch their files one at a time
    @Test
    fun `it should fetch uploads from storage on init`() {
        val manager = newManager(false)

        manager.init()

        TODO()
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

        val uploadRequest = randomUploadRequest()

        manager.upload(uploadRequest).get()

        verify(uploadPersistenceManager).add(uploadRequest.upload)
    }

    //TODO transfer event tests

    @Test
    fun `adding a new upload should emit an UploadAdded event`() {
        val manager = newManager(false)

        val uploadRequest = randomUploadRequest()

        val event = TransferEvent.UploadAdded(uploadRequest.upload, UploadTransferState.QUEUED)
        assertEventEmitted(manager, event) {
            manager.upload(uploadRequest).get()
        }
    }

    @Test
    fun `it should not attempt to start uploads when network is unavailable`() {
        val manager = newManager(false)

        val uploadRequest = randomUploadRequest()

        manager.upload(uploadRequest).get()

        transferOperations.assertCreateNotCalled()
    }

    @Test
    fun `it should immediately start an upload if queue space is available and the network is available`() {
        val manager = newManager()

        val uploadRequest = randomUploadRequest()

        manager.upload(uploadRequest).get()

        transferOperations.assertCreateCalled(uploadRequest.upload, uploadRequest.file)
    }

    @Test
    fun `it should start queued uploads when network becomes available`() {
        val manager = newManager(false)

        val uploadRequest = randomUploadRequest()

        manager.upload(uploadRequest).get()

        networkStatus.onNext(true)

        transferOperations.assertCreateCalled(uploadRequest.upload, uploadRequest.file)
    }

    @Test
    fun `it should update upload state when creation succeeds`() {
        val manager = newManager()

        val uploadRequest = randomUploadRequest()

        transferOperations.autoResolveCreate = true

        manager.upload(uploadRequest).get()

        verify(uploadPersistenceManager).setState(uploadRequest.upload.id, UploadState.CREATED)
    }

    @Test
    fun `it should upload parts once upload has been created`() {
        val manager = newManager()

        val uploadRequest = randomUploadRequest()

        transferOperations.autoResolveCreate = true

        manager.upload(uploadRequest).get()

        val upload = uploadRequest.upload
        transferOperations.assertUploadPartCalled(upload.copy(state = UploadState.CREATED), upload.parts[0], uploadRequest.file)
    }

    @Test
    fun `it should move an upload to completion state once all parts have been uploaded`() {
        val manager = newManager()

        val uploadRequest = randomUploadRequest()

        transferOperations.autoResolveCreate = true

        manager.upload(uploadRequest).get()

        transferOperations.getUploadPartDeferred(1).resolve(Unit)

        verify(uploadPersistenceManager).setState(uploadRequest.upload.id, UploadState.COMPLETE)
    }

    @Test
    fun `it should emit a TransferEvent when moving an upload to completion state`() {
        val manager = newManager()

        val uploadRequest = randomUploadRequest()

        transferOperations.autoResolveCreate = true

        val upload = uploadRequest.upload
        val updated = upload.markPartCompleted(1).copy(
            state = UploadState.COMPLETE
        )

        assertEventEmitted(manager, TransferEvent.UploadStateChanged(updated, UploadTransferState.COMPLETE)) {
            manager.upload(uploadRequest).get()

            transferOperations.getUploadPartDeferred(1).resolve(Unit)
        }
    }
}