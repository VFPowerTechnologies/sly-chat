package io.slychat.messenger.services

import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.messaging.MessengerService
import rx.Observable
import rx.Subscription

class FileListSyncWatcherImpl(
    syncEvents: Observable<FileListSyncEvent>,
    private val messengerService: MessengerService
) : FileListSyncWatcher {
    private var subscription: Subscription? = null

    init {
        subscription = syncEvents.ofType(FileListSyncEvent.Result::class.java).subscribe { onSyncEvent(it) }
    }

    private fun onSyncEvent(event: FileListSyncEvent.Result) {
        if (event.result.remoteUpdatesPerformed <= 0)
            return

        messengerService.broadcastFileListSync()
    }

    override fun init() {
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }
}