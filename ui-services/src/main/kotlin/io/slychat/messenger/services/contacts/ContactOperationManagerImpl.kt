package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.alwaysUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.*

class ContactOperationManagerImpl(
    networkAvailable: Observable<Boolean>,
    private val contactSyncJobFactory: ContactSyncJobFactory
) : ContactOperationManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private var currentRunningJob: ContactSyncJob? = null
    private var queuedSync: ContactSyncJobDescription? = null

    private val runningSubject = PublishSubject.create<ContactSyncJobInfo>()
    override val running: Observable<ContactSyncJobInfo> = runningSubject

    private var isNetworkAvailable: Boolean = false

    private val networkAvailableSubscription: Subscription

    private var isOperationRunning = false
    private val isSyncRunning: Boolean
        get() = currentRunningJob != null

    private var pendingOperations = ArrayDeque<() -> Promise<*, Exception>>()

    init {
        networkAvailableSubscription = networkAvailable.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        processNext()
    }

    override fun shutdown() {
        networkAvailableSubscription.unsubscribe()
    }

    override fun runOperation(operation: () -> Promise<*, Exception>) {
        pendingOperations.add(operation)
        processNext()
    }

    override fun withCurrentSyncJob(body: ContactSyncJobDescription.() -> Unit) {
        val queuedJob = this.queuedSync
        val job = if (queuedJob != null)
            queuedJob
        else {
            val desc = ContactSyncJobDescription()
            this.queuedSync = desc
            desc
        }

        job.body()

        processNext()
    }

    /** Process the next queued job, if any. */
    private fun nextSyncJob() {
        currentRunningJob = null
        processSyncJob()
    }

    /** Process the next queued job if no job is currently running. */
    private fun processSyncJob() {
        if (isSyncRunning)
            return

        val queuedJob = this.queuedSync ?: return

        val job = contactSyncJobFactory.create()

        log.info("Beginning contact sync job")

        val p = job.run(queuedJob)

        currentRunningJob = job
        this.queuedSync = null

        val info = ContactSyncJobInfo(
            queuedJob.updateRemote,
            queuedJob.localSync,
            queuedJob.remoteSync,
            true
        )

        runningSubject.onNext(info)

        p success {
            log.info("Contact job completed successfully")
        } fail { e ->
            log.error("Contact job failed: {}", e.message, e)
        } alwaysUi {
            runningSubject.onNext(info.copy(isRunning = false))
            currentRunningJob = null
            processNext()
        }

        return
    }

    private fun processNext() {
        if (isOperationRunning || isSyncRunning)
            return

        if (pendingOperations.isEmpty()) {
            if (isNetworkAvailable)
                nextSyncJob()

            return
        }

        log.debug("Beginning operation")

        val operation = pendingOperations.pop()

        isOperationRunning = true

        operation() alwaysUi {
            log.debug("Operation complete")
            isOperationRunning = false
            processNext()
        }
    }

}