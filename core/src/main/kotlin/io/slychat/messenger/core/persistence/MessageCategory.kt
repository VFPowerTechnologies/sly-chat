package io.slychat.messenger.core.persistence

/** General category of messages. */
enum class MessageCategory {
    /** Single recipient text message. */
    TEXT_SINGLE,
    /** Group text message. */
    TEXT_GROUP,
    /** All other messages (group control messages, etc). */
    OTHER
}