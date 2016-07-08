package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.FetchContactResponse
import io.slychat.messenger.core.persistence.ContactInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface ContactsService {
    val contactEvents: Observable<ContactEvent>

    fun shutdown()

    fun fetchRemoteContactInfo(email: String?, queryPhoneNumber: String?): Promise<FetchContactResponse, Exception>
    /** Add a new non-pending contact for which we already have info. */
    fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception>
    /** Remove the given contact from the contact list. */
    fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception>
    fun updateContact(contactInfo: ContactInfo): Promise<Unit, Exception>
    fun allowMessagesFrom(users: Set<UserId>): Promise<Set<UserId>, Exception>

    fun doProcessUnaddedContacts()
    fun doRemoteSync()
    fun doLocalSync()
}