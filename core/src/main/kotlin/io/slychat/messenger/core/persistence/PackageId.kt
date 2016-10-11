package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.SlyAddress

data class PackageId(
    val address: SlyAddress,
    val messageId: String
)