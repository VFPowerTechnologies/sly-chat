package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.ciphers.Key

data class ReceivedAttachment(
    val n: Int,
    val theirFileId: String,
    val theirShareKey: String,
    val fileKey: Key
)