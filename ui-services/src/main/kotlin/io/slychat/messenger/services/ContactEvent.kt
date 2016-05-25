package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo

interface ContactEvent

class ContactsAdded(val contacts: Set<ContactInfo>) : ContactEvent
//if was pending, delete stuff (happens once a user rejects adding
class ContactsRemoved(val contacts: List<ContactInfo>) : ContactEvent
class ContactsModified(val contacts: List<ContactInfo>) : ContactEvent
class ContactRequests(val contacts: Set<ContactInfo>) : ContactEvent
//sent by contact lookup for invalid ids
class InvalidContacts(val contacts: Set<UserId>) : ContactEvent
