package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.UIContactDetails
import com.vfpowertech.keytap.ui.services.dummy.InvalidContactException
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import java.util.HashMap

class ContactsServiceImpl(
    private val contactsPersistenceManager: ContactsPersistenceManager
) : ContactsService {

    private var contacts = HashMap<Int, UIContactDetails>()

    private fun fetchContacts() {
        contacts.clear()

        val contactList = contactsPersistenceManager.getAll().get()

        for(i in contactList.indices){
            contacts.put(i, UIContactDetails(i, contactList[i].name, contactList[i].phoneNumber, contactList[i].email, contactList[i].publicKey))
        }
    }

    override fun updateContact(newContactDetails: UIContactDetails) {
        if (newContactDetails.id == null)
            throw IllegalArgumentException("Contact id was null")

        contacts[newContactDetails.id] ?: throw InvalidContactException(newContactDetails)

        contactsPersistenceManager.update(ContactInfo(newContactDetails.email, newContactDetails.name, newContactDetails.phoneNumber, newContactDetails.publicKey))
        contacts[newContactDetails.id] = newContactDetails
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        fetchContacts()

        return Promise.ofSuccess<List<UIContactDetails>, Exception>(contacts.values.toList())
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception>{
        if (contactDetails.id != null)
            throw IllegalArgumentException("Contact id was not null")

        return contactsPersistenceManager.add(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey)) bind {
            val id = contacts.size
            val withId = contactDetails.copy(id = id)
            contacts.put(id, contactDetails)
            Promise.ofSuccess<UIContactDetails, Exception>(withId)
        }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        if (contactDetails.id == null)
            throw IllegalArgumentException("Contact id was null")

        contacts[contactDetails.id] ?: throw InvalidContactException(contactDetails)

        return contactsPersistenceManager.remove(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey)) bind {
            fetchContacts()
            Promise.ofSuccess<Unit, Exception>(Unit)
        }
    }
}