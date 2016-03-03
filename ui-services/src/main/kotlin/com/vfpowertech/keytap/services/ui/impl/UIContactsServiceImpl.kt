package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.http.api.contacts.FetchContactResponse
import com.vfpowertech.keytap.core.http.api.contacts.NewContactRequest
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.ui.UINewContactResult
import com.vfpowertech.keytap.services.ui.UIContactsService
import com.vfpowertech.keytap.services.ui.UIContactDetails
import com.vfpowertech.keytap.services.ui.UINewContactDetails
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi

class UIContactsServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String
) : UIContactsService {
    private val contactClient = ContactClientWrapper(serverUrl)
    private var cachedNewContact = UIContactDetails("", "", "", "");

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

    override fun addNewContact(publicKey: String): Promise<UIContactDetails, Exception> {
        if (this.cachedNewContact.publicKey == publicKey) {
            val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
            return contactsPersistenceManager.add(ContactInfo(this.cachedNewContact.email, this.cachedNewContact.name, this.cachedNewContact.phoneNumber, this.cachedNewContact.publicKey)) map {
                this.cachedNewContact
            }
        }

        return Promise.ofSuccess(this.cachedNewContact)
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

        return newContactRequest successUi { response ->
            if (response.errorMessage == null) {
                val contactInfo = response.contactInfo!!
                this.cachedNewContact = UIContactDetails(contactInfo.name, contactInfo.phoneNumber, contactInfo.username, contactInfo.publicKey)
            }
        } bind  { response ->
            if (response.errorMessage != null) {
                Promise.ofSuccess<UINewContactResult, Exception>(UINewContactResult(false, response.errorMessage, null))
            }
            else {
                val contactInfo = response.contactInfo!!
                Promise.ofSuccess<UINewContactResult, Exception>(UINewContactResult(true, null, UINewContactDetails(contactInfo.name, contactInfo.publicKey)))
            }
        }
    }
}