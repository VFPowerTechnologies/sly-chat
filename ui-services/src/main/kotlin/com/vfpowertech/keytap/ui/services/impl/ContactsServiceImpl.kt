package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.UIContactDetails
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi

class ContactsServiceImpl(
    private val contactsPersistenceManager: ContactsPersistenceManager
) : ContactsService {

    override fun updateContact(newContactDetails: UIContactDetails) {
        contactsPersistenceManager.update(ContactInfo(newContactDetails.email, newContactDetails.name, newContactDetails.phoneNumber, newContactDetails.publicKey))
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        return contactsPersistenceManager.getAll() map { contacts ->
            contacts.map { c -> UIContactDetails(c.name, c.phoneNumber, c.email, c.publicKey) }
        } successUi { uiContacts ->
            Promise.ofSuccess<List<UIContactDetails>, Exception>(uiContacts)
        }
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception>{
        return contactsPersistenceManager.add(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey)) map {
            contactDetails
        } successUi { contactDetails ->
            Promise.ofSuccess<UIContactDetails, Exception>(contactDetails)
        }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        return contactsPersistenceManager.remove(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey))
    }
}