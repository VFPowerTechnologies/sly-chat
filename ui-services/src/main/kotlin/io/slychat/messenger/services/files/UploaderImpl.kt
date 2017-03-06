package io.slychat.messenger.services.files

import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.UploadError
import io.slychat.messenger.core.persistence.UploadInfo
import io.slychat.messenger.core.persistence.UploadPersistenceManager
import io.slychat.messenger.core.persistence.UploadState
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.io.FileNotFoundException

class UploaderImpl(
    initialSimulUploads: Int,
    private val uploadPersistenceManager: UploadPersistenceManager,
    private val transferOperations: TransferOperations,
    networkStatus: Observable<Boolean>
) : Uploader {
    private val log = LoggerFactory.getLogger(javaClass)

    private val list = TransferList<UploadStatus>(initialSimulUploads)

    override var simulUploads: Int
        get() = list.maxSize
        set(value) {
            list.maxSize = value
        }

    override val uploads: List<UploadStatus>
        get() = list.all.values.toList()

    private val subject = PublishSubject.create<TransferEvent>()

    override val events: Observable<TransferEvent>
        get() = subject

    private var subscription: Subscription? = null

    private var isNetworkAvailable = false

    init {
        subscription = networkStatus.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isAvailable)
            startNextUpload()
    }

    override fun init() {
        uploadPersistenceManager.getAll() successUi {
            addUploads(it)
        } failUi {
            log.error("Failed to fetch initial uploads: {}", it.message, it)
        }
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun addUploads(info: Iterable<UploadInfo>) {
        info.forEach {
            val upload = it.upload
            val file = it.file
            if (upload.id in list.all)
                error("Upload ${upload.id} already in transfer list")

            val initialState = if (upload.error == null) {
                if (upload.state == UploadState.COMPLETE)
                    TransferState.COMPLETE
                else
                    TransferState.QUEUED
            }
            else
                TransferState.ERROR

            list.all[upload.id] = UploadStatus(
                upload,
                file,
                initialState,
                upload.parts.map { UploadPartTransferProgress(0, it.size) }
            )

            if (initialState == TransferState.QUEUED)
                list.queued.add(upload.id)

            subject.onNext(TransferEvent.UploadAdded(upload, initialState))
        }

        startNextUpload()
    }

    override fun upload(info: UploadInfo): Promise<Unit, Exception> {
        return uploadPersistenceManager.add(info) mapUi {
            addUploads(listOf(info))
        }
    }

    private fun startNextUpload() {
        if (!isNetworkAvailable)
            return

        if (!list.canActivateMore) {
            log.info("{}/{} uploads running, not starting more", list.active.size, list.maxSize)
            return
        }

        while (list.canActivateMore) {
            val next = list.nextQueued()

            val nextId = next.upload.id
            list.active.add(nextId)

            val newState = TransferState.ACTIVE
            list.setStatus(nextId, next.copy(state = newState))

            subject.onNext(TransferEvent.UploadStateChanged(next.upload, newState))

            nextStep(nextId)
        }
    }

    private fun nextStep(uploadId: String) {
        val status = list.getStatus(uploadId)

        when (status.upload.state) {
            UploadState.PENDING -> createUpload(status)
            //for now we just upload the next available part sequentially
            UploadState.CREATED -> uploadNextPart(status)
            //this shouldn't get here, but it's here for completion
            UploadState.COMPLETE -> log.warn("nextStep called with state=COMPLETE state")
        }
    }

    private fun markUploadComplete(status: UploadStatus) {
        log.info("Marking upload {} as complete", status.upload.id)

        val uploadId = status.upload.id
        list.active.remove(uploadId)
        list.inactive.add(uploadId)

        updateTransferState(uploadId, TransferState.COMPLETE)
    }

    private fun updateUploadState(uploadId: String, newState: UploadState): Promise<Unit, Exception> {
        return uploadPersistenceManager.setState(uploadId, newState) mapUi {
            updateCachedUploadState(uploadId, newState)
        } failUi {
            log.error("Failed to update upload {} state to {}: {}", uploadId, newState, it.message, it)
            moveUploadToErrorState(uploadId, UploadError.UNKNOWN)
        }
    }

    private fun updateCachedUploadState(uploadId: String, newState: UploadState) {
        list.updateStatus(uploadId) {
            it.copy(
                it.upload.copy(state = newState)
            )
        }
    }

    private fun updateTransferState(uploadId : String, newState: TransferState) {
        val status = list.updateStatus(uploadId) {
            it.copy(state = newState)
        }

        subject.onNext(TransferEvent.UploadStateChanged(status.upload, newState))
    }

    private fun moveUploadToErrorState(uploadId: String, uploadError: UploadError) {
        log.info("Moving upload {} to error state", uploadId)

        list.updateStatus(uploadId) {
            it.copy(
                upload = it.upload.copy(error = uploadError)
            )
        }

        list.active.remove(uploadId)
        list.queued.remove(uploadId)
        list.inactive.add(uploadId)

        updateTransferState(uploadId, TransferState.ERROR)
    }

    //TODO
    //XXX this is called on a diff thread
    //just keep track of last time we emitted data, and emit every second
    //the last time it occurs doesn't matter (since we end up completing)
    //XXX for testing this this is kinda difficult though... although I guess just override the current time works?
    //not sure using an observable really makes things any easier anyways
    private fun receivePartProgress(uploadId: String, partN: Int, transferedBytes: Long) {

    }

    private fun handleUploadException(uploadId: String, e: Exception, origin: String) {
        val uploadError = when (e) {
            is FileNotFoundException -> UploadError.FILE_DISAPPEARED

            is InsufficientQuotaException -> UploadError.INSUFFICIENT_QUOTA

            is UploadCorruptedException -> UploadError.CORRUPTED

            else -> {
                if (isNotNetworkError(e))
                    UploadError.UNKNOWN
                else
                    UploadError.NETWORK_ISSUE
            }
        }

        log.condError(uploadError == UploadError.UNKNOWN, "{} failed: {}", origin, e.message, e)

        uploadPersistenceManager.setError(uploadId, uploadError) successUi {
            moveUploadToErrorState(uploadId, uploadError)
        } fail {
            log.error("Failed to set upload error for {}: {}", uploadId, it.message, it)
        }
    }

    private fun uploadNextPart(status: UploadStatus) {
        val nextPart = status.upload.parts.find { !it.isComplete }
        val uploadId = status.upload.id
        if (nextPart == null) {
            updateUploadState(uploadId, UploadState.COMPLETE) mapUi {
                markUploadComplete(status)
            }
            return
        }

        //(for later)
        //it could occur that this is called after status.upload is modified (eg: another part completes) if transfering
        //multiple parts; however we don't actually use .parts since we pass in the part explicitly so this isn't an issue
        //we should probably mapUi, because if something caused the upload to fail we don't wanna do anything
        transferOperations.uploadPart(status.upload, nextPart, status.file) {
            receivePartProgress(status.upload.id, nextPart.n, it)
        } bind {
            uploadPersistenceManager.completePart(uploadId, nextPart.n)
        } successUi {
            completePart(uploadId, nextPart.n)
            nextStep(uploadId)
        } failUi { e ->
            handleUploadException(uploadId, e, "uploadPart")
        }
    }

    //XXX for progress, check if part's marked as complete, and drop it
    //since we schedule stuff to be run, it'll occur that we complete a part before the final progress update comes in

    private fun completePart(uploadId: String, n: Int) {
        val status = list.getStatus(uploadId)

        val newProgress = status.progress.mapIndexed { i, progress ->
            if (i == (n - 1))
                progress.copy(transferedBytes = progress.totalBytes)
            else
                progress
        }

        list.setStatus(uploadId, status.copy(
            upload = status.upload.markPartCompleted(n),
            progress = newProgress
        ))

        val transferProgress = UploadTransferProgress(newProgress, status.transferedBytes, status.totalBytes)
        subject.onNext(TransferEvent.UploadProgress(status.upload, transferProgress))
    }

    private fun createUpload(status: UploadStatus) {
        val uploadId = status.upload.id

        transferOperations.create(status.upload, status.file) bind {
            updateUploadState(uploadId, UploadState.CREATED)
        } successUi {
            nextStep(uploadId)
        } failUi {
            handleUploadException(uploadId, it, "create")
        }
    }

    override fun clearError(uploadId: String): Promise<Unit, Exception> {
        return uploadPersistenceManager.setError(uploadId, null) successUi {
            list.inactive.remove(uploadId)
            list.queued.add(uploadId)

            val status = list.updateStatus(uploadId) {
                it.copy(
                    upload = it.upload.copy(error = null),
                    state = TransferState.QUEUED
                )
            }

            subject.onNext(TransferEvent.UploadStateChanged(status.upload, status.state))

            startNextUpload()
        }
    }
}