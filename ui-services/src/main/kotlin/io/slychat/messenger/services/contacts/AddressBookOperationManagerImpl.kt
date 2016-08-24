package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.*

/**
 * Whenever an address book sync is requested, the SyncScheduler is notified.
 * Address book syncs are only actually performed once the SyncScheduler emits an event.
 */
class AddressBookOperationManagerImpl(
    networkAvailable: Observable<Boolean>,
    private val addressBookSyncJobFactory: AddressBookSyncJobFactory,
    private val syncScheduler: SyncScheduler
) : AddressBookOperationManager {
    private class PendingOperation<out T>(
        private val operation: () -> Promise<T, Exception>
    ) {
        private val d: Deferred<T, Exception> = deferred()

        fun run(): Promise<T, Exception> {
            return operation() success { d.resolve(it) } fail { d.reject(it) }
        }

        val promise: Promise<T, Exception> = d.promise
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private var currentRunningJob: AddressBookSyncJob? = null
    private var queuedSync: AddressBookSyncJobDescription? = null

    private val runningSubject = PublishSubject.create<AddressBookSyncEvent>()
    override val syncEvents: Observable<AddressBookSyncEvent>
        get() = runningSubject

    private var isNetworkAvailable: Boolean = false

    private val networkAvailableSubscription: Subscription

    private var isOperationRunning = false
    private val isSyncRunning: Boolean
        get() = currentRunningJob != null

    //if we've notified the scheduler
    private var wasSyncScheduleEventReceived = false

    private var pendingOperations = ArrayDeque<PendingOperation<*>>()

    init {
        networkAvailableSubscription = networkAvailable.subscribe { onNetworkStatusChange(it) }

        syncScheduler.scheduledEvent.subscribe { onSyncScheduledEvent() }
    }

    private fun onSyncScheduledEvent() {
        wasSyncScheduleEventReceived = true
        processNext()
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        processNext()
    }

    override fun shutdown() {
        networkAvailableSubscription.unsubscribe()
    }

    override fun <T> runOperation(operation: () -> Promise<T, Exception>): Promise<T, Exception> {
        val pendingOperation = PendingOperation(operation)
        pendingOperations.add(pendingOperation)
        processNext()

        return pendingOperation.promise
    }

    override fun withCurrentSyncJob(body: AddressBookSyncJobDescription.() -> Unit) {
        val queuedJob = this.queuedSync
        val job = if (queuedJob != null)
            queuedJob
        else {
            val desc = AddressBookSyncJobDescription()
            this.queuedSync = desc
            desc
        }

        job.body()

        processNext()
    }

    override fun withCurrentSyncJobNoScheduler(body: AddressBookSyncJobDescription.() -> Unit) {
        wasSyncScheduleEventReceived = true
        withCurrentSyncJob(body)
    }

    private fun scheduleSync() {
        syncScheduler.schedule()
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

        if (!wasSyncScheduleEventReceived) {
            scheduleSync()
            return
        }

        wasSyncScheduleEventReceived = false

        val job = addressBookSyncJobFactory.create()

        log.info("Beginning contact sync job")

        val p = job.run(queuedJob)

        currentRunningJob = job
        this.queuedSync = null

        val info = AddressBookSyncJobInfo(
            queuedJob.push,
            queuedJob.findPlatformContacts,
            queuedJob.pull
        )

        runningSubject.onNext(AddressBookSyncEvent.Begin(info))

        p successUi {
            log.info("Address book sync completed successfully")
            syncCompleted(info, it)
        } failUi { e ->
            log.error("Address book sync job failed: {}", e.message, e)
            syncCompleted(info, AddressBookSyncResult(false, 0, false))
        }

        return
    }

    private fun syncCompleted(info: AddressBookSyncJobInfo, result: AddressBookSyncResult) {
        runningSubject.onNext(AddressBookSyncEvent.End(info, result))
        currentRunningJob = null
        processNext()
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

        operation.run() alwaysUi {
            log.debug("Operation complete")
            isOperationRunning = false
            processNext()
        }
    }

}