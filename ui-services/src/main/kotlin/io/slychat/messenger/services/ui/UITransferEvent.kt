package io.slychat.messenger.services.ui

import io.slychat.messenger.services.files.TransferEvent
import io.slychat.messenger.services.files.TransferState

enum class UITransferEventType {
    ADD,
    REMOVE,
    PROGRESS,
    STATE_CHANGE,
    UNTIL_RETRY
}

sealed class UITransferEvent {
    abstract val type: UITransferEventType

    class Added(val transfer: UITransfer, val state: TransferState) : UITransferEvent() {
        override val type: UITransferEventType
            get() = UITransferEventType.ADD
    }

    class Removed(val transfers: List<UITransfer>) : UITransferEvent() {
        override val type: UITransferEventType
            get() = UITransferEventType.REMOVE
    }

    class Progress(val transfer: UITransfer, val progress: UITransferProgress) : UITransferEvent() {
        override val type: UITransferEventType
            get() = UITransferEventType.PROGRESS
    }

    class StateChanged(val transfer: UITransfer, val state: TransferState) : UITransferEvent() {
        override val type: UITransferEventType
            get() = UITransferEventType.STATE_CHANGE
    }

    class UntilRetry(val transfer: UITransfer, val remainingSecs: Long) : UITransferEvent() {
        override val type: UITransferEventType
            get() = UITransferEventType.UNTIL_RETRY
    }
}

fun TransferEvent.toUI(): UITransferEvent {
    return when (this) {
        is TransferEvent.Added -> UITransferEvent.Added(transfer.toUI(), state)
        is TransferEvent.Removed -> UITransferEvent.Removed(transfers.map { it.toUI() })
        is TransferEvent.Progress -> UITransferEvent.Progress(transfer.toUI(), progress.toUI())
        is TransferEvent.StateChanged -> UITransferEvent.StateChanged(transfer.toUI(), state)
        is TransferEvent.UntilRetry -> UITransferEvent.UntilRetry(transfer.toUI(), remainingSecs)
    }
}