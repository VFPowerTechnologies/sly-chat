package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.UIContactInfo
import nl.komponents.kovenant.Promise

class ContactsServiceImpl : ContactsService {
    private val contacts = hashMapOf(
        0 to UIContactInfo(0, "Contact A", "000-000-0000", "a@a.com"),
        1 to UIContactInfo(1, "Contact B", "111-111-1111", "b@b.com")
    )

    override fun updateContact(newContactInfo: UIContactInfo) {
        if (newContactInfo.id == null)
            throw IllegalArgumentException("Contact id was null")

        synchronized(this) {
            val contact = contacts[newContactInfo.id] ?: throw InvalidContactException(newContactInfo)
            contacts[newContactInfo.id] = newContactInfo
        }
    }

    override fun getContacts(): Promise<List<UIContactInfo>, Exception> {
        synchronized(this) {
            return Promise.ofSuccess(contacts.values.toList())
        }
    }

    override fun addNewContact(contactInfo: UIContactInfo): Promise<UIContactInfo, Exception> {
        if (contactInfo.id != null)
            throw IllegalArgumentException("Contact id was not null")

        synchronized(this) {
            val id = contacts.size
            val withId = contactInfo.copy(id = id)
            contacts[id] = withId
            return Promise.ofSuccess(withId)
        }
    }
}

