package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo

interface ContactEvent {
    class Added(val contacts: Set<ContactInfo>) : ContactEvent
    //if was pending, delete stuff (happens once a user rejects adding
    class Removed(val contacts: List<ContactInfo>) : ContactEvent
    class Modified(val contacts: List<ContactInfo>) : ContactEvent
    class Request(val contacts: Set<ContactInfo>) : ContactEvent
    //sent by contact lookup for invalid ids
    class InvalidContacts(val contacts: Set<UserId>) : ContactEvent
    class Sync(val isRunning: Boolean) : ContactEvent
}
