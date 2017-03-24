package io.slychat.messenger.core.persistence

interface TransferError {
    val isTransient: Boolean
}