package io.slychat.messenger.services

import io.slychat.messenger.services.contacts.AddressBookSyncEvent
import io.slychat.messenger.services.messaging.MessengerService
import rx.Observable
import rx.Subscription

class AddressBookSyncWatcherImpl(
    syncEvents: Observable<AddressBookSyncEvent>,
    private val messengerService: MessengerService
) : AddressBookSyncWatcher {
    private var subscription: Subscription? = null

    init {
        subscription = syncEvents.subscribe { onSyncEvent(it) }
    }

    override fun init() {}

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun onSyncEvent(event: AddressBookSyncEvent) {
        when (event) {
            is AddressBookSyncEvent.Begin -> {}

            is AddressBookSyncEvent.End -> {
                if (event.result.updateCount > 0)
                    messengerService.broadcastSync()
            }
        }
    }
}