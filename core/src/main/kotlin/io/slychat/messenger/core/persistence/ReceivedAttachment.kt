package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.UserMetadata

data class ReceivedAttachment(
    val n: Int,
    val fileId: String,
    val theirShareKey: String,
    val userMetadata: UserMetadata
)