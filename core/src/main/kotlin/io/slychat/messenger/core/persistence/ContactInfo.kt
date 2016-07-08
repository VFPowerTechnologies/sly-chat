package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/** Describes what types of messages are allowed from a given contact. */
enum class AllowedMessageLevel(val level: Int) {
    /** Discard all messages from this user. */
    BLOCKED(0),
    /** Allow process group messages from this user. Private messages are discarded. */
    GROUP_ONLY(1),
    /** Both group and private messages from this user are processed. */
    ALL(2);

    companion object {
        fun fromInt(v: Int): AllowedMessageLevel = when (v) {
            0 -> BLOCKED
            1 -> GROUP_ONLY
            2 -> ALL
            else -> throw IllegalArgumentException("Invalid integer value for AllowedMessageLevel: $v")
        }
    }
}

data class ContactInfo(
    val id: UserId,
    val email: String,
    val name: String,
    val allowedMessageLevel: AllowedMessageLevel,
    val isPending: Boolean,
    val phoneNumber: String?,
    val publicKey: String
)