package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.KeyTapApplication
import com.vfpowertech.keytap.ui.services.UIContactDetails
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map

class ContactsServiceImpl(
    private val app: KeyTapApplication
) : ContactsService {

    private fun getContactsPersistenceManagerOrThrow(): ContactsPersistenceManager =
        app.userComponent?.contactsPersistenceManager ?: error("No UserComponent available")

    override fun updateContact(newContactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.update(ContactInfo(newContactDetails.email, newContactDetails.name, newContactDetails.phoneNumber, newContactDetails.publicKey)) map { newContactDetails }
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.getAll() map { contacts ->
            contacts.map { c -> UIContactDetails(c.name, c.phoneNumber, c.email, c.publicKey) }
        }
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.add(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey)) map {
            contactDetails
        }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.remove(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey))
    }
}