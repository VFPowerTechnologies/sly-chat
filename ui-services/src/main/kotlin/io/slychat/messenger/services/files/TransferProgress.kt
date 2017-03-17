package io.slychat.messenger.services.files

interface TransferProgress {
    val transferedBytes: Long
    val totalBytes: Long
}