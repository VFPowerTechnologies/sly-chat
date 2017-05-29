package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/** A received, encrypted message from another user. */
data class Package(
    val id: PackageId,
    val timestamp: Long,
    val payload: String
) {
    val userId: UserId
        get() = id.address.id
}