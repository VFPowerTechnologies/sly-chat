package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.UIContactDetails
import nl.komponents.kovenant.Promise

class DummyContactsService : ContactsService {
    private val contacts = hashMapOf(
        "Contact A" to UIContactDetails("Contact A", "000-000-0000", "a@a.com", "dummyPublicKey"),
        "Contact B" to UIContactDetails("Contact B", "111-111-1111", "b@b.com", "dummyPublicKedy")
    )

    override fun updateContact(newContactDetails: UIContactDetails) {
        synchronized(this) {
            newContactDetails
        }
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        synchronized(this) {
            return Promise.ofSuccess(contacts.values.toList())
        }
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        synchronized(this) {
            return Promise.ofSuccess(contactDetails)
        }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }
}

