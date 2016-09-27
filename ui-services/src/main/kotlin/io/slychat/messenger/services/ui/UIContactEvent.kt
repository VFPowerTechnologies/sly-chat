package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId

enum class UIContactEventType {
    ADD,
    REMOVE,
    UPDATE,
    SYNC,
    BLOCKED,
    UNBLOCKED
}

sealed class UIContactEvent {
    abstract val type: UIContactEventType

    class Added(val contacts: List<UIContactInfo>) : UIContactEvent() {
        override val type: UIContactEventType
            get() = UIContactEventType.ADD
    }

    class Removed(val contacts: List<UIContactInfo>) : UIContactEvent() {
        override val type: UIContactEventType
            get() = UIContactEventType.REMOVE
    }

    class Updated(val contacts: List<UIContactInfo>) : UIContactEvent() {
        override val type: UIContactEventType
            get() = UIContactEventType.UPDATE
    }

    class Sync(val isRunning: Boolean) : UIContactEvent() {
        override val type: UIContactEventType
            get() = UIContactEventType.SYNC
    }

    class Blocked(val userId: UserId) : UIContactEvent() {
        override val type: UIContactEventType
            get() = UIContactEventType.BLOCKED
    }

    class Unblocked(val userId: UserId) : UIContactEvent() {
        override val type: UIContactEventType
            get() = UIContactEventType.UNBLOCKED
    }
}
