package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import nl.komponents.kovenant.Promise

interface ContactService {

    fun addContactListener(listener: ((ContactEvent) -> Unit))

    fun clearListeners()

    fun getContacts(): Promise<MutableMap<UserId, ContactInfo>, Exception>

    fun getContact(id: UserId): Promise<ContactInfo?, Exception>

    fun blockContact(id: UserId): Promise<Unit, Exception>

    fun deleteContact(contactInfo: ContactInfo): Promise<Boolean, Exception>

    fun getContactCount(): Promise<Int, Exception>

    fun fetchNewContactInfo(username: String): Promise<ContactInfo?, Exception>

    fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception>
}