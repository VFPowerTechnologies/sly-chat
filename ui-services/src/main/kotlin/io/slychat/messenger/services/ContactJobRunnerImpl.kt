package io.slychat.messenger.services

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.alwaysUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.*

class ContactJobRunnerImpl(
    networkAvailable: Observable<Boolean>,
    private val contactJobFactory: ContactJobFactory
) : ContactJobRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    private var currentRunningJob: ContactJob? = null
    private var queuedJob: ContactJobDescription? = null

    private val runningSubject = PublishSubject.create<ContactJobInfo>()
    override val running: Observable<ContactJobInfo> = runningSubject

    private var isNetworkAvailable: Boolean = false

    private val networkAvailableSubscription: Subscription

    private var isOperationRunning = false
    private val isJobRunning: Boolean
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

    override fun withCurrentJob(body: ContactJobDescription.() -> Unit) {
        val queuedJob = this.queuedJob
        val job = if (queuedJob != null)
            queuedJob
        else {
            val desc = ContactJobDescription()
            this.queuedJob = desc
            desc
        }

        job.body()

        processNext()
    }

    /** Process the next queued job, if any. */
    private fun nextJob() {
        currentRunningJob = null
        processJob()
    }

    /** Process the next queued job if no job is currently running. */
    private fun processJob() {
        if (isJobRunning)
            return

        val queuedJob = this.queuedJob ?: return

        val job = contactJobFactory.create()

        log.info("Beginning contact sync job")

        val p = job.run(queuedJob)

        currentRunningJob = job
        this.queuedJob = null

        val info = ContactJobInfo(
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
        if (isOperationRunning || isJobRunning)
            return

        if (pendingOperations.isEmpty()) {
            if (isNetworkAvailable)
                nextJob()

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