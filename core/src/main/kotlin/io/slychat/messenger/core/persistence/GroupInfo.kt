package io.slychat.messenger.core.persistence

/** Group metadata. */
data class GroupInfo(
    val id: GroupId,
    val name: String,
    val membershipLevel: GroupMembershipLevel
)