package io.slychat.messenger.core.persistence

data class ReceivedMessageInfo(
    val message: String,
    val sentTimestamp: Long
)