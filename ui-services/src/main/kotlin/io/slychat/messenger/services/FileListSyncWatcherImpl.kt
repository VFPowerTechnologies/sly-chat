package io.slychat.messenger.services

import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.Transfer
import io.slychat.messenger.services.files.TransferEvent
import io.slychat.messenger.services.files.TransferState
import io.slychat.messenger.services.messaging.MessengerService
import rx.Observable
import rx.subscriptions.CompositeSubscription

class FileListSyncWatcherImpl(
    syncEvents: Observable<FileListSyncEvent>,
    transferEvents: Observable<TransferEvent>,
    private val messengerService: MessengerService
) : FileListSyncWatcher {
    private var subscriptions = CompositeSubscription()

    init {
        subscriptions += syncEvents.ofType(FileListSyncEvent.Result::class.java).subscribe { onSyncEvent(it) }
        subscriptions += transferEvents.ofType(TransferEvent.StateChanged::class.java).subscribe { onTransferEvent(it) }
    }

    private fun onSyncEvent(event: FileListSyncEvent.Result) {
        if (event.result.remoteUpdatesPerformed <= 0)
            return

        messengerService.broadcastFileListSync()
    }

    private fun onTransferEvent(event: TransferEvent.StateChanged) {
        if (event.state != TransferState.COMPLETE)
            return

        when (event.transfer) {
            is Transfer.U ->
                messengerService.broadcastFileListSync()
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }
}