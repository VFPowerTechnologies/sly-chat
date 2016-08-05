package io.slychat.messenger.services.contacts

import rx.Observable

/** Used in tandem with AddressBookOperationManagerImpl to schedule contact syncs, preventing multiple repeated syncs. */
interface SyncScheduler {
    val scheduledEvent: Observable<Unit>

    fun schedule()
}