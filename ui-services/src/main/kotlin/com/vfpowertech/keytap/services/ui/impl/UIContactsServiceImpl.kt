package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.http.api.contacts.ContactAsyncClient
import com.vfpowertech.keytap.core.http.api.contacts.FetchContactResponse
import com.vfpowertech.keytap.core.http.api.contacts.NewContactRequest
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.ui.UIContactDetails
import com.vfpowertech.keytap.services.ui.UIContactsService
import com.vfpowertech.keytap.services.ui.UINewContactResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map

class UIContactsServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String
) : UIContactsService {
    private val contactClient = ContactAsyncClient(serverUrl)

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

    override fun fetchNewContactInfo(email: String?, phoneNumber: String?): Promise<UINewContactResult, Exception> {
        if (email == null && phoneNumber == null) {
            return Promise.ofSuccess(UINewContactResult(false, "Username or phone number must be provided", null))
        }

        val authToken = app.userComponent?.userLoginData?.authToken!!
        val newContactRequest: Promise<FetchContactResponse, Exception>

        newContactRequest = contactClient.fetchNewContactInfo(NewContactRequest(authToken, email, phoneNumber))

        return newContactRequest map  { response ->
            if (response.errorMessage != null) {
                UINewContactResult(false, response.errorMessage, null)
            }
            else {
                val contactInfo = response.contactInfo!!
                UINewContactResult(true, null, UIContactDetails(contactInfo.name, contactInfo.phoneNumber, contactInfo.username, contactInfo.publicKey))
            }
        }
    }
}