package io.slychat.messenger.services.files

import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.Subscriber
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

//TODO need to react to file deletions (or prevent them while downloads attached to them exist)
class DownloaderImpl(
    initialSimulDownloads: Int,
    private val downloadPersistenceManager: DownloadPersistenceManager,
    private val downloadOperations: DownloadOperations,
    private val timerScheduler: Scheduler,
    private val mainScheduler: Scheduler,
    initialNetworkStatus: Boolean
) : Downloader {
    companion object {
        internal const val PROGRESS_TIME_MS = 1000L
    }

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

    private val downloadSubscriptions = HashMap<String, Subscription>()

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
                when (download.state) {
                    DownloadState.COMPLETE -> TransferState.COMPLETE
                    DownloadState.CANCELLED -> TransferState.CANCELLED
                    DownloadState.CREATED -> TransferState.QUEUED
                }
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

    private fun handleDownloadException(downloadId: String, e: Throwable) {
        if (e is CancellationException) {
            downloadPersistenceManager.setState(downloadId, DownloadState.CANCELLED) successUi {
                markDownloadCancelled(downloadId)
            } fail {
                log.error("Unable to mark download as cancelled: {}", it.message, it)
            }

            return
        }

        val downloadError = when (e) {
            is FileMissingException -> DownloadError.REMOTE_FILE_MISSING

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

    private fun removeDownloadSubscription(downloadId: String) {
        log.debug("Removing download subscription for {}", downloadId)
        downloadSubscriptions.remove(downloadId)?.unsubscribe()
    }

    private fun startDownload(downloadId: String) {
        val status = list.getStatus(downloadId)

        when (status.download.state) {
            DownloadState.CREATED -> {
                if (downloadId in downloadSubscriptions)
                    error("Attempt to start duplicate download $downloadId")

                 val subscription = downloadOperations.download(status.download, status.file)
                    .buffer(PROGRESS_TIME_MS, TimeUnit.MILLISECONDS, timerScheduler)
                    .map { it.sum() }
                    .observeOn(mainScheduler)
                    .subscribe(object : Subscriber<Long>() {
                        override fun onError(e: Throwable) {
                            removeDownloadSubscription(downloadId)
                            handleDownloadException(downloadId, e)
                        }

                        override fun onCompleted() {
                            removeDownloadSubscription(downloadId)

                            downloadPersistenceManager.setState(downloadId, DownloadState.COMPLETE) successUi {
                                markDownloadComplete(downloadId)
                            } fail {
                                log.error("Failed to update download state: {}", it.message, it)
                            }
                        }

                        override fun onNext(t: Long) {
                            receiveProgress(downloadId, t)
                        }
                    })

                downloadSubscriptions[downloadId] = subscription
            }

            DownloadState.COMPLETE -> log.warn("startDownload called with a completed download")

            DownloadState.CANCELLED -> log.warn("startDownload called with a cancelled download")
        }
    }

    private fun markDownloadCancelled(downloadId: String) {
        log.info("Marking download {} as cancelled", downloadId)

        list.updateStatus(downloadId) {
            it.copy(
                download = it.download.copy(state = DownloadState.CANCELLED)
            )
        }

        list.queued.remove(downloadId)
        list.active.remove(downloadId)
        list.inactive.add(downloadId)

        updateTransferState(downloadId, TransferState.CANCELLED)
    }

    private fun markDownloadComplete(downloadId: String) {
        log.info("Marking download {} as complete", downloadId)

        list.updateStatus(downloadId) {
            it.copy(
                download = it.download.copy(state = DownloadState.COMPLETE)
            )
        }

        list.active.remove(downloadId)
        list.inactive.add(downloadId)

        updateTransferState(downloadId, TransferState.COMPLETE)
    }

    private fun receiveProgress(downloadId: String, transferedBytes: Long) {
        val status = list.updateStatus(downloadId) {
            it.copy(progress = it.progress.add(transferedBytes))
        }

        subject.onNext(TransferEvent.DownloadProgress(status.download, status.progress))
    }

    override fun shutdown() {
        downloadSubscriptions.forEach { t, u ->
            u.unsubscribe()
        }

        downloadSubscriptions.clear()
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

    override fun cancel(downloadId: String): Boolean {
        if (downloadId !in list.all)
            throw InvalidDownloadException(downloadId)

        val sub = downloadSubscriptions[downloadId] ?: return false

        sub.unsubscribe()

        return true
    }
}