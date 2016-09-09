package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

enum class UIMessageUpdateEventType {
    DELIVERED
}

sealed class UIMessageUpdateEvent {
    abstract val type: UIMessageUpdateEventType

    class Delivered(val userId: UserId?, val groupId: GroupId?, val messageId: String, val deliveredTimestamp: Long) : UIMessageUpdateEvent() {
        override val type: UIMessageUpdateEventType
            get() = UIMessageUpdateEventType.DELIVERED
    }
}