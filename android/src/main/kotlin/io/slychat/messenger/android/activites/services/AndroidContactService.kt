package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import nl.komponents.kovenant.Promise

interface AndroidContactService {

    fun addContactListener(listener: ((ContactEvent) -> Unit))

    fun clearListeners()

    fun getAll(): Promise<List<ContactInfo>, Exception>

    fun getContacts(): Promise<MutableMap<UserId, ContactInfo>, Exception>

    fun getContact(id: UserId): Promise<ContactInfo?, Exception>

    fun getBlockedContacts(): Promise<List<ContactInfo>, Exception>

    fun blockContact(id: UserId): Promise<Unit, Exception>

    fun deleteContact(contactInfo: ContactInfo): Promise<Boolean, Exception>

    fun unblockContact(id: UserId): Promise<Unit, Exception>

    fun getContactCount(): Promise<Int, Exception>

    fun fetchNewContactInfo(username: String): Promise<ContactInfo?, Exception>

    fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception>

    fun allowAll(userId: UserId): Promise<Unit, Exception>
}