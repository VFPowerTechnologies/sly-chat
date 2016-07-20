package io.slychat.messenger.core.persistence

enum class GroupMembershipLevel(val level: Int) {
    /** Discard all messages and invitations to this group. */
    BLOCKED(0),
    /** Discard all messages, but process invitations. */
    PARTED(1),
    /** Accept all messages. */
    JOINED(2);

    companion object {
        fun fromInt(v: Int): GroupMembershipLevel = when (v) {
            0 -> BLOCKED
            1 -> PARTED
            2 -> JOINED
            else -> throw IllegalArgumentException("Invalid integer value for MembershipLevel: $v")
        }
    }
}