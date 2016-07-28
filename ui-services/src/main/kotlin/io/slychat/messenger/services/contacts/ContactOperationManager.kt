package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import rx.Observable

/**
 * Manages running contact and group operations, as well as remote sync jobs.
 *
 * Operations will be queued if a sync is running, and syncs will be queued if operations are running.
 *
 * If a sync is currently running, another sync may be queued withCurrentSyncJob. Subsequent calls to this function
 * will update the queued sync job.
 *
 * Sync jobs won't be run if the network is offline, but operations don't have this restriction.
 *
 * Pending operations all have precedence over pending sync jobs.
 */
interface ContactOperationManager {
    /** Fires once with isRunning = true on sync start, isRunning = false on sync completion (regardless of outcome). */
    val running: Observable<ContactSyncJobInfo>

    fun withCurrentSyncJob(body: ContactSyncJobDescription.() -> Unit)

    fun shutdown()

    /** Queues an operation to be run. */
    fun <T> runOperation(operation: () -> Promise<T, Exception>): Promise<T, Exception>
}