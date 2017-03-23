package io.slychat.messenger.services.files

interface TransferProgress {
    /** May be < 0 if no file is associated with this transfer. */
    val transferedBytes: Long

    /** May be < 0 if no file is associated with this transfer. */
    val totalBytes: Long
}