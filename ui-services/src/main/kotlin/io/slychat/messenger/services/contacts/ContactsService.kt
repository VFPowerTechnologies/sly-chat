package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.FindContactResponse
import io.slychat.messenger.core.persistence.ContactInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface ContactsService {
    val contactEvents: Observable<ContactEvent>

    fun get(userId: UserId): Promise<ContactInfo?, Exception>
    fun getAll(): Promise<List<ContactInfo>, Exception>

    fun shutdown()

    fun fetchRemoteContactInfo(email: String?, queryPhoneNumber: String?): Promise<FindContactResponse, Exception>
    /** Add a new non-pending contact for which we already have info. */
    fun addByInfo(contactInfo: ContactInfo): Promise<Boolean, Exception>
    fun addById(userId: UserId): Promise<Boolean, Exception>
    fun addSelf(selfInfo: ContactInfo): Promise<Unit, Exception>

    /** Remove the given contact from the contact list. */
    fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception>
    fun updateContact(contactInfo: ContactInfo): Promise<Unit, Exception>
    fun filterBlocked(users: Set<UserId>): Promise<Set<UserId>, Exception>

    fun getBlockList(): Promise<Set<UserId>, Exception>
    fun block(userId: UserId): Promise<Unit, Exception>
    fun unblock(userId: UserId): Promise<Unit, Exception>

    fun allowAll(userId: UserId): Promise<Unit, Exception>

    /** Adds contact data for any missing user in the set. Should do nothing for the empty set. */
    fun addMissingContacts(users: Set<UserId>): Promise<Set<UserId>, Exception>

    fun doAddressBookPull()
    fun doFindPlatformContacts()
}