package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile

data class TransferStatus(val transfer: Transfer, val file: RemoteFile?, val state: TransferState, val progress: TransferProgress) {
    val id: String
        get() = transfer.id
}
