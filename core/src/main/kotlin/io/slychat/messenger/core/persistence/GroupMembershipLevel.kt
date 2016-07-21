package io.slychat.messenger.core.persistence

enum class GroupMembershipLevel {
    /** Discard all messages and invitations to this group. */
    BLOCKED,
    /** Discard all messages, but process invitations. */
    PARTED,
    /** Accept all messages. */
    JOINED;
}