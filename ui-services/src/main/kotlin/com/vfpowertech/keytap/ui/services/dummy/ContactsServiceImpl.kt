package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.UIContactDetails
import nl.komponents.kovenant.Promise

class ContactsServiceImpl : ContactsService {
    private val contacts = hashMapOf(
        0 to UIContactDetails(0, "Contact A", "000-000-0000", "a@a.com"),
        1 to UIContactDetails(1, "Contact B", "111-111-1111", "b@b.com")
    )

    override fun updateContact(newContactDetails: UIContactDetails) {
        if (newContactDetails.id == null)
            throw IllegalArgumentException("Contact id was null")

        synchronized(this) {
            val contact = contacts[newContactDetails.id] ?: throw InvalidContactException(newContactDetails)
            contacts[newContactDetails.id] = newContactDetails
        }
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        synchronized(this) {
            return Promise.ofSuccess(contacts.values.toList())
        }
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        if (contactDetails.id != null)
            throw IllegalArgumentException("Contact id was not null")

        synchronized(this) {
            val id = contacts.size
            val withId = contactDetails.copy(id = id)
            contacts[id] = withId
            return Promise.ofSuccess(withId)
        }
    }
}

