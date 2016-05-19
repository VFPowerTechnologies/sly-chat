package io.slychat.messenger.core.persistence

data class Package(
    val id: PackageId,
    val timestamp: Long,
    val payload: String
)