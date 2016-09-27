package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo

interface ContactEvent {
    class Added(val contacts: Set<ContactInfo>) : ContactEvent
    class Removed(val contacts: Set<ContactInfo>) : ContactEvent
    class Updated(val contacts: Set<ContactUpdate>) : ContactEvent
    class Sync(val isRunning: Boolean) : ContactEvent
    class Blocked(val userId: UserId) : ContactEvent
    class Unblocked(val userId: UserId) : ContactEvent
}
