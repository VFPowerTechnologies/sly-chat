package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.enforceExhaustive
import io.slychat.messenger.core.http.api.upload.NewUploadError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.Subscriber
import rx.subjects.PublishSubject
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UploaderImpl(
    initialSimulUploads: Int,
    private val uploadPersistenceManager: UploadPersistenceManager,
    private val uploadOperations: UploadOperations,
    private val timerScheduler: Scheduler,
    private val mainScheduler: Scheduler,
    initialNetworkStatus: Boolean
) : Uploader {
    companion object {
        internal const val PROGRESS_TIME_MS = 1000L
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val list = TransferList<UploadStatus>(initialSimulUploads)

    private val cancellationTokens = HashMap<String, AtomicBoolean>()

    override var simulUploads: Int
        get() = list.maxSize
        set(value) {
            list.maxSize = value
        }

    override var isNetworkAvailable = initialNetworkStatus
        set(value) {
            field = value

            if (value)
                startNextUpload()
        }

    private val quotaSubject = PublishSubject.create<Quota>()

    override val quota: Observable<Quota>
        get() = quotaSubject

    override val uploads: List<UploadStatus>
        get() = list.all.values.toList()

    private val subject = PublishSubject.create<TransferEvent>()

    override val events: Observable<TransferEvent>
        get() = subject

    private val awaitingCancellation = HashSet<String>()

    override fun init() {
        uploadPersistenceManager.getAll() successUi {
            addUploads(it)
        } failUi {
            log.error("Failed to fetch initial uploads: {}", it.message, it)
        }
    }

    override fun shutdown() {
    }

    private fun addUploads(info: Iterable<UploadInfo>) {
        info.forEach {
            val upload = it.upload
            val file = it.file
            if (upload.id in list.all)
                error("Upload ${upload.id} already in transfer list")

            val initialState = if (upload.error == null) {
                when (upload.state) {
                    UploadState.PENDING -> TransferState.QUEUED
                    UploadState.CREATED -> TransferState.QUEUED
                    UploadState.CACHING -> TransferState.QUEUED
                    UploadState.COMPLETE -> TransferState.COMPLETE
                    UploadState.CANCELLING -> TransferState.QUEUED
                    UploadState.CANCELLED -> TransferState.CANCELLED
                }
            }
            else
                TransferState.ERROR

            list.all[upload.id] = UploadStatus(
                upload,
                file,
                initialState,
                upload.parts.map { UploadPartTransferProgress(if (it.isComplete) it.remoteSize else 0, it.remoteSize) }
            )

            if (initialState == TransferState.QUEUED || initialState == TransferState.CANCELLING)
                list.queued.add(upload.id)
            else
                list.inactive.add(upload.id)

            subject.onNext(TransferEvent.Added(upload, initialState))
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

            val status = list.getStatus(nextId)

            val newState = if (status.upload.state != UploadState.CANCELLING)
                TransferState.ACTIVE
            else
                TransferState.CANCELLING

            list.setStatus(nextId, next.copy(state = newState))

            subject.onNext(TransferEvent.StateChanged(next.upload, newState))

            nextStep(nextId)
        }
    }

    private fun nextStep(uploadId: String) {
        val status = list.getStatus(uploadId)

        if (uploadId in awaitingCancellation) {
            awaitingCancellation -= uploadId

            moveUploadToCancellingState(uploadId) successUi {
                nextStep(uploadId)
            } failUi {
                log.error("Failed to move upload to cancelling state")
                //shouldn't occur
                moveUploadToErrorState(uploadId, UploadError.UNKNOWN)
            }

            return
        }

        when (status.upload.state) {
            UploadState.PENDING -> createUpload(status)
            UploadState.CACHING -> cacheFile(status)
            //for now we just upload the next available part sequentially
            UploadState.CREATED -> uploadNextPart(status)
            UploadState.CANCELLING -> cancelUpload(status)
            //this shouldn't get here, but it's here for completion
            UploadState.COMPLETE -> error("nextStep called with state=COMPLETE")
            UploadState.CANCELLED -> error("nextStep called with state=CANCELLED")
        }.enforceExhaustive()
    }

    private fun cancelUpload(status: UploadStatus) {
        val uploadId = status.upload.id
        log.info("Cancelling upload {}", uploadId)

        uploadOperations.cancel(status.upload) bindUi {
            log.info("Upload successfully cancelled")
            moveUploadToState(uploadId, TransferState.CANCELLED, UploadState.CANCELLED)
        } mapUi {
            list.updateStatus(uploadId) {
                it.copy(upload = it.upload.copy(fileId = null),  file = null)
            }

            list.active -= uploadId
            list.inactive += uploadId
        } failUi {
            handleUploadException(uploadId, it, "cancelUpload")
        }
    }

    private fun cacheFile(status: UploadStatus) {
        val uploadId = status.upload.id

        val cancellationToken = AtomicBoolean()

        uploadOperations.cache(status.upload, status.file!!, cancellationToken)
            .buffer(PROGRESS_TIME_MS, TimeUnit.MILLISECONDS, timerScheduler)
            .map { it.sum() }
            .observeOn(mainScheduler)
            .subscribe(object : Subscriber<Long>() {
                override fun onNext(t: Long) {
                    //TODO progress
                }

                override fun onCompleted() {
                    removeCancellationToken(uploadId)
                    log.info("Caching for $uploadId completed")
                    updateUploadState(uploadId, UploadState.CREATED) successUi {
                        nextStep(uploadId)
                    } fail {
                        log.error("Failed to move")
                    }
                }

                override fun onError(e: Throwable) {
                    handleUploadException(uploadId, e, "cacheFile")
                }
            })

        cancellationTokens[uploadId] = cancellationToken
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
                it.upload.copy(state = newState, error = null)
            )
        }
    }

    private fun updateTransferState(uploadId: String, newState: TransferState) {
        val status = list.updateStatus(uploadId) {
            it.copy(state = newState)
        }

        subject.onNext(TransferEvent.StateChanged(status.upload, newState))
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

    private fun receivePartProgress(uploadId: String, partN: Int, transferedBytes: Long) {
        val status = list.updateStatus(uploadId) {
            val progress = it.progress.mapIndexed { i, uploadPartTransferProgress ->
                if (i == partN - 1)
                    uploadPartTransferProgress.add(transferedBytes)
                else
                    uploadPartTransferProgress
            }

            it.copy(progress = progress)
        }

        val progress = UploadTransferProgress(status.progress, status.transferedBytes, status.totalBytes)
        subject.onNext(TransferEvent.Progress(status.upload, progress))
    }

    private fun removeCancellationToken(uploadId: String) {
        cancellationTokens.remove(uploadId)
    }

    private fun handleUploadException(uploadId: String, e: Throwable, origin: String) {
        if (e is CancellationException) {
            moveUploadToCancellingState(uploadId) successUi {
                nextStep(uploadId)
            }

            return
        }

        val uploadError = when (e) {
            is FileNotFoundException -> UploadError.FILE_DISAPPEARED

            is InsufficientQuotaException -> UploadError.INSUFFICIENT_QUOTA

            is DuplicateFilePathException -> UploadError.DUPLICATE_FILE

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
            val p = if (status.upload.isSinglePart)
                Promise.ofSuccess(Unit)
            else
                uploadOperations.complete(status.upload)

            p successUi {
                awaitingCancellation -= uploadId

                updateUploadState(uploadId, UploadState.COMPLETE) mapUi {
                    markUploadComplete(status)
                }
            } failUi {
                handleUploadException(status.upload.id, it, "completeUpload")
            }

            return
        }

        val cancellationToken = AtomicBoolean()

        log.info("Upload {}/{} starting", uploadId, nextPart.n)

        uploadOperations.uploadPart(status.upload, nextPart, status.file!!, cancellationToken)
            .buffer(PROGRESS_TIME_MS, TimeUnit.MILLISECONDS, timerScheduler)
            .map { it.sum() }
            .observeOn(mainScheduler)
            .subscribe(object : Subscriber<Long>() {
                override fun onError(e: Throwable) {
                    removeCancellationToken(uploadId)
                    handleUploadException(uploadId, e, "uploadPart")
                }

                override fun onNext(t: Long) {
                    receivePartProgress(uploadId, nextPart.n, t)
                }

                override fun onCompleted() {
                    removeCancellationToken(uploadId)
                    log.info("Upload $uploadId/${nextPart.n} completed")

                    uploadPersistenceManager.completePart(uploadId, nextPart.n) successUi {
                        completePart(uploadId, nextPart.n)
                        nextStep(uploadId)
                    } fail {
                        log.error("Failed to mark part as complete: {}", it.message, it)
                    }
                }
            })

        cancellationTokens[uploadId] = cancellationToken
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
        subject.onNext(TransferEvent.Progress(status.upload, transferProgress))
    }

    private fun createUpload(status: UploadStatus) {
        val uploadId = status.upload.id
        val file = status.file

        uploadOperations.create(status.upload, file!!) bindUi {
            quotaSubject.onNext(it.quota)

            when (it.error) {
                NewUploadError.INSUFFICIENT_QUOTA ->
                    throw InsufficientQuotaException()

                NewUploadError.DUPLICATE_FILE ->
                    throw DuplicateFilePathException(file.userMetadata.directory, file.userMetadata.fileName)

                null -> {}
            }.enforceExhaustive()

            val nextState = if (status.upload.isEncrypted)
                UploadState.CACHING
            else
                UploadState.CREATED

            updateUploadState(uploadId, nextState)
        } successUi {
            nextStep(uploadId)
        } failUi {
            handleUploadException(uploadId, it, "create")
        }
    }

    private fun onErrorCleared(uploadId: String) {
        list.inactive.remove(uploadId)
        list.queued.add(uploadId)

        val status = list.updateStatus(uploadId) {
            it.copy(
                upload = it.upload.copy(error = null),
                state = TransferState.QUEUED
            )
        }

        subject.onNext(TransferEvent.StateChanged(status.upload, status.state))

        startNextUpload()

    }

    override fun clearError(uploadId: String): Promise<Unit, Exception> {
        if (list.getStatus(uploadId).state != TransferState.ERROR)
            return Promise.ofSuccess(Unit)

        return uploadPersistenceManager.setError(uploadId, null) successUi {
            onErrorCleared(uploadId)
        }
    }

    override fun remove(uploadIds: List<String>): Promise<Unit, Exception> {
        if (uploadIds.isEmpty())
            return Promise.ofSuccess(Unit)

        val statuses = uploadIds.map { uploadId ->
            list.all[uploadId] ?: throw InvalidUploadException(uploadId)
        }

        val ids = statuses.map { status ->
            val id = status.upload.id
            if (id !in list.inactive)
                throw IllegalStateException("Upload $id is currently active, can't remove")

            list.queued.remove(id)
            list.inactive.remove(id)

            id
        }

        return uploadPersistenceManager.remove(ids) successUi {
            ids.forEach { list.all.remove(it) }

            subject.onNext(TransferEvent.Removed(statuses.map { Transfer.U(it.upload) }))
        }
    }

    override fun contains(transferId: String): Boolean {
        return transferId in list.all
    }

    private fun moveUploadToState(uploadId: String, transferState: TransferState, uploadState: UploadState): Promise<Unit, Exception> {
        return updateUploadState(uploadId, uploadState) mapUi {
            updateTransferState(uploadId, transferState)
        } fail {
            log.error("Failed to move upload to transfer={}/upload={} state: {}", transferState, uploadState, it.message, it)
        }
    }

    private fun moveUploadToCancellingState(uploadId: String): Promise<Unit, Exception> {
        return moveUploadToState(uploadId, TransferState.CANCELLING, UploadState.CANCELLING)
    }

    //in this case we don't need to cancel running transfers/etc, we just need to alter the internal state
    private fun attemptCancelQueued(status: UploadStatus) {
        val uploadId = status.upload.id
        log.info("Attempting to cancel queued upload: {}", uploadId)

        @Suppress("IMPLICIT_CAST_TO_ANY")
        when (status.upload.state) {
            //hasn't been pushed remotely yet, so just cancel it
            UploadState.PENDING -> {
                moveUploadToState(uploadId, TransferState.CANCELLED, UploadState.CANCELLED)

                list.queued.remove(uploadId)
                list.inactive.add(uploadId)
            }

            //TODO handle cached file deletion somehow
            UploadState.CREATED, UploadState.CACHING -> {
                //TODO we actually wanna move out of the queue while we update; or update then persist, which is a better idea
                moveUploadToCancellingState(uploadId)
            }

            //do nothing
            UploadState.COMPLETE, UploadState.CANCELLING, UploadState.CANCELLED -> {}
        }.enforceExhaustive()
    }

    private fun attemptCancelError(status: UploadStatus) {
        val uploadId = status.upload.id
        log.info("Attempting to cancel upload with error: {}", uploadId)

        when (status.upload.state) {
            UploadState.CACHING, UploadState.CREATED, UploadState.CANCELLING -> {
                moveUploadToCancellingState(uploadId) successUi {
                    onErrorCleared(uploadId)
                }
            }

            UploadState.PENDING -> moveUploadToState(uploadId, TransferState.CANCELLED, UploadState.CANCELLED)

            UploadState.CANCELLED -> error("Cancelled upload in ERROR state")

            UploadState.COMPLETE -> error("Completed upload in ERROR state")
        }.enforceExhaustive()
    }

    private fun attemptCancelActive(status: UploadStatus) {
        val uploadId = status.upload.id
        log.info("Attempting to cancel active upload: {}", uploadId)

        when (status.upload.state) {
            UploadState.PENDING -> awaitingCancellation += uploadId
            UploadState.CACHING -> requestTransferCancellation(uploadId)
            UploadState.CREATED -> requestTransferCancellation(uploadId)
            UploadState.CANCELLING -> {}
            UploadState.COMPLETE -> error("COMPLETE upload in active list")
            UploadState.CANCELLED -> error("CANCELLED upload in active list")
        }.enforceExhaustive()
    }

    private fun requestTransferCancellation(uploadId: String) {
        cancellationTokens[uploadId]?.set(true)

        //incase the transfer completes before reading the token, or if we're waiting on a persistence update
        awaitingCancellation += uploadId
    }

    override fun cancel(uploadId: String) {
        val status = list.getStatus(uploadId)

        //we let the move to cancelling state itself not persist for simplicity but that shouldn't generally be an issue

        when (status.state) {
            TransferState.QUEUED -> attemptCancelQueued(status)
            TransferState.ACTIVE -> attemptCancelActive(status)
            TransferState.CANCELLING -> {}
            TransferState.CANCELLED -> {}
            TransferState.COMPLETE -> {}
            //will always be in inactive
            TransferState.ERROR -> attemptCancelError(status)
        }.enforceExhaustive()
    }

    //for test purposes
    internal fun get(uploadId: String): UploadStatus? {
        return list.all[uploadId]
    }
}