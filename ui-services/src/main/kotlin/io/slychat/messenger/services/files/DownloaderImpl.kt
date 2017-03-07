package io.slychat.messenger.services.files

import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.DownloadError
import io.slychat.messenger.core.persistence.DownloadInfo
import io.slychat.messenger.core.persistence.DownloadPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

//TODO need to react to file deletions (or prevent them while downloads attached to them exist)
class DownloaderImpl(
    initialSimulDownloads: Int,
    private val downloadPersistenceManager: DownloadPersistenceManager,
    private val downloadOperations: DownloadOperations,
    initialNetworkStatus: Boolean
) : Downloader {
    private val log = LoggerFactory.getLogger(javaClass)

    private val list = TransferList<DownloadStatus>(initialSimulDownloads)

    private val subject = PublishSubject.create<TransferEvent>()

    override var isNetworkAvailable = initialNetworkStatus
        set(value) {
            field = value

            if (value)
                startNextDownload()
        }

    override val events: Observable<TransferEvent>
        get() = subject

    override val downloads: List<DownloadStatus>
        get() = list.all.values.toList()

    override var simulDownloads: Int
        get() = list.maxSize
        set(value) {
            list.maxSize = value
        }

    override fun init() {
        downloadPersistenceManager.getAll() successUi {
            addDownloads(it)
        } failUi {
            log.error("Failed to fetch initial downloads: {}", it.message, it)
        }
    }

    private fun addDownloads(info: List<DownloadInfo>) {
        info.forEach {
            val download = it.download
            val file = it.file
            if (download.id in list.all)
                error("Download ${download.id} already in transfer list")

            val initialState = if (download.error == null) {
                if (download.isComplete)
                    TransferState.COMPLETE
                else
                    TransferState.QUEUED
            }
            else
                TransferState.ERROR

            list.all[download.id] = DownloadStatus(
                download,
                file,
                initialState,
                //TODO we need to fetch this from disk? always do this when adding to queue; need to inc count but not
                //actually start download until a file size is established
                DownloadTransferProgress(0, file.remoteFileSize)
            )

            if (initialState == TransferState.QUEUED)
                list.queued.add(download.id)

            subject.onNext(TransferEvent.DownloadAdded(download, initialState))
        }

        startNextDownload()
    }

    private fun startNextDownload() {
        if (!isNetworkAvailable)
            return

        if (!list.canActivateMore) {
            log.info("{}/{} downloads running, not starting more", list.active.size, list.maxSize)
            return
        }

        while (list.canActivateMore) {
            val next = list.nextQueued()

            val nextId = next.download.id
            list.active.add(nextId)

            val newState = TransferState.ACTIVE
            list.setStatus(nextId, next.copy(state = newState))

            subject.onNext(TransferEvent.DownloadStateChange(next.download, newState))

            startDownload(nextId)
        }
    }

    private fun handleDownloadException(downloadId: String, e: Exception) {
        val downloadError = when (e) {
            is FileMissingException -> DownloadError.REMOTE_FILE_MISSING

            is CancellationException -> DownloadError.CANCELLED

            else -> {
                if (isNotNetworkError(e))
                    DownloadError.UNKNOWN
                else
                    DownloadError.NETWORK_ISSUE
            }
        }

        log.condError(downloadError == DownloadError.UNKNOWN, "Download failed: {}", e.message, e)

        downloadPersistenceManager.setError(downloadId, downloadError) successUi {
            moveUploadToErrorState(downloadId, downloadError)
        } fail {
            log.error("Failed to set download error for {}: {}", downloadId, it.message, it)
        }
    }

    private fun startDownload(downloadId: String) {
        val status = list.getStatus(downloadId)

        if (status.download.isComplete) {
            log.warn("startDownload called with a completed download")
        }
        else {
            //TODO
            downloadOperations.download(status.download, status.file, AtomicBoolean()) {
                receiveProgress(downloadId, it)
            } bind {
                downloadPersistenceManager.setComplete(downloadId, true)
            } successUi {
                markDownloadComplete(downloadId)
            } failUi {
                handleDownloadException(downloadId, it)
            }
        }
    }

    private fun markDownloadComplete(downloadId: String) {
        log.info("Marking download {} as complete", downloadId)

        list.updateStatus(downloadId) {
            it.copy(
                download = it.download.copy(isComplete = true)
            )
        }

        list.active.remove(downloadId)
        list.inactive.add(downloadId)

        updateTransferState(downloadId, TransferState.COMPLETE)
    }

    private fun receiveProgress(downloadId: String, transferedBytes: Long) {
    }

    override fun shutdown() {
        TODO()
    }

    override fun download(info: DownloadInfo): Promise<Unit, Exception> {
        return downloadPersistenceManager.add(info.download) successUi {
            addDownloads(listOf(info))
        }
    }

    override fun clearError(downloadId: String): Promise<Unit, Exception> {
        return downloadPersistenceManager.setError(downloadId, null) successUi {
            list.inactive.remove(downloadId)
            list.queued.add(downloadId)

            val status = list.updateStatus(downloadId) {
                it.copy(
                    download = it.download.copy(error = null),
                    state = TransferState.QUEUED
                )
            }

            subject.onNext(TransferEvent.DownloadStateChange(status.download, status.state))

            startNextDownload()
        }
    }

    private fun updateTransferState(downloadId: String, newState: TransferState) {
        val status = list.updateStatus(downloadId) {
            it.copy(state = newState)
        }

        subject.onNext(TransferEvent.DownloadStateChange(status.download, newState))
    }

    private fun moveUploadToErrorState(downloadId: String, downloadError: DownloadError) {
        log.info("Moving download {} to error state", downloadId)

        list.updateStatus(downloadId) {
            it.copy(
                download = it.download.copy(error = downloadError)
            )
        }

        list.active.remove(downloadId)
        list.queued.remove(downloadId)
        list.inactive.add(downloadId)

        updateTransferState(downloadId, TransferState.ERROR)
    }
}