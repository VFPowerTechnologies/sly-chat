package io.slychat.messenger.core.persistence

/** Describes what types of messages are allowed from a given contact. */
enum class AllowedMessageLevel {
    /** Discard all messages from this user. */
    BLOCKED,
    /** Allow process group messages from this user. Private messages are discarded. */
    GROUP_ONLY,
    /** Both group and private messages from this user are processed. */
    ALL;
}