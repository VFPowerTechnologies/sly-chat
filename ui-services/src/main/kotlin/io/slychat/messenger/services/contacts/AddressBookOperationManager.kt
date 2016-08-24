package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import rx.Observable

/**
 * Manages running contact and group operations, as well as remote sync jobs.
 *
 * Operations will be queued if a sync is running, and syncs will be queued if operations are running.
 *
 * If a sync is currently running, another sync may be queued withCurrentSyncJob. Subsequent calls to this function
 * will update the queued sync job. The sync job will be run sometime in the future. If the job must be run as soon as
 * possible (eg: for initial sync), then withCurrentSynbJobNoScheduler may be used.
 *
 * Sync jobs won't be run if the network is offline, but operations don't have this restriction.
 *
 * Pending operations all have precedence over pending sync jobs.
 */
interface AddressBookOperationManager {
    val syncEvents: Observable<AddressBookSyncEvent>

    fun withCurrentSyncJob(body: AddressBookSyncJobDescription.() -> Unit)

    /** Runs the given job as soon as possible. Used on startup for initial sync. */
    fun withCurrentSyncJobNoScheduler(body: AddressBookSyncJobDescription.() -> Unit)

    fun shutdown()

    /** Queues an operation to be run. */
    fun <T> runOperation(operation: () -> Promise<T, Exception>): Promise<T, Exception>
}