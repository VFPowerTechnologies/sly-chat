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

    private var isPendingRunning = false
    private var pendingOperations = ArrayDeque<() -> Promise<*, Exception>>()

    init {
        networkAvailableSubscription = networkAvailable.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        processNextPendingOperation()
    }

    override fun shutdown() {
        networkAvailableSubscription.unsubscribe()
    }

    override fun runOperation(operation: () -> Promise<*, Exception>) {
        pendingOperations.add(operation)
        processNextPendingOperation()
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

        if (currentRunningJob == null && !isPendingRunning && isNetworkAvailable)
            nextJob()
    }

    /** Process the next queued job, if any. */
    private fun nextJob() {
        currentRunningJob = null
        processJob()
    }

    /** Process the next queued job if no job is currently running and the network is active. */
    private fun processJob() {
        if (currentRunningJob != null)
            return

        val queuedJob = this.queuedJob ?: return

        val job = contactJobFactory.build()

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
            processNextPendingOperation()
        }

        return
    }

    private fun processNextPendingOperation() {
        if (isPendingRunning)
            return

        if (pendingOperations.isEmpty()) {
            if (isNetworkAvailable)
                nextJob()

            return
        }

        val operation = pendingOperations.pop()

        isPendingRunning = true

        operation() alwaysUi {
            isPendingRunning = false
            processNextPendingOperation()
        }
    }

}