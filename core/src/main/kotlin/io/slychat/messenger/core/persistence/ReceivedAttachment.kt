package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.UserMetadata

data class ReceivedAttachment(
    val n: Int,
    val theirFileId: String,
    val theirShareKey: String,
    val ourFileId: String,
    val userMetadata: UserMetadata
)