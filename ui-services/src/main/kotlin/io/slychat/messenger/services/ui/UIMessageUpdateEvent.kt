package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

enum class UIMessageUpdateEventType {
    DELIVERED,
    EXPIRING,
    EXPIRED,
    DELETED,
    DELETED_ALL
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

    class Deleted(val userId: UserId?, val groupId: GroupId?, val messageIds: List<String>) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.DELETED
    }

    class DeletedAll(val userId: UserId?, val groupId: GroupId?) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.DELETED_ALL
    }
}