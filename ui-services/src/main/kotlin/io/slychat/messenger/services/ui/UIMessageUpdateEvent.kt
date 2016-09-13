package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

enum class UIMessageUpdateEventType {
    DELIVERED,
    EXPIRING,
    EXPIRED
}

sealed class UIMessageUpdateEvent {
    abstract val type: UIMessageUpdateEventType

    class Delivered(val userId: UserId?, val groupId: GroupId?, val messageId: String, val deliveredTimestamp: Long) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.DELIVERED
    }

    class Expiring(val userId: UserId?, val groupId: GroupId?, val messageId: String, val ttl: Long, val expiresAt: Long) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.EXPIRING
    }

    class Expired(val userId: UserId?, val groupId: GroupId?, val messageId: String) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.EXPIRED
    }
}