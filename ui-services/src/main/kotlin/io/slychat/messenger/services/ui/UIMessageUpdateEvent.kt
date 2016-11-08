package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.MessageSendFailure

enum class UIMessageUpdateEventType {
    DELIVERED,
    DELIVERY_FAILED,
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

    class DeliveryFailed(val userId: UserId?, val groupId: GroupId?, val messageId: String, val failures: Map<UserId, MessageSendFailure>) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.DELIVERY_FAILED
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