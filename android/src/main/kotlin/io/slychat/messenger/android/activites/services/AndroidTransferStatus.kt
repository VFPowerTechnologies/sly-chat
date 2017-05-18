package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.services.files.Transfer
import io.slychat.messenger.services.files.TransferProgress
import io.slychat.messenger.services.files.TransferState

data class AndroidTransferStatus(
    val transfer: Transfer,
    val file: RemoteFile?,
    var state: TransferState,
    var progress: TransferProgress,
    var untilRetry: Long?) {

    val id: String
        get() = transfer.id
}