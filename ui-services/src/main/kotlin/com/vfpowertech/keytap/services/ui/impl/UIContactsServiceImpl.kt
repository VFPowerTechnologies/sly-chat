package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.http.api.contacts.*
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.ui.UIContactDetails
import com.vfpowertech.keytap.services.ui.UIContactsService
import com.vfpowertech.keytap.services.ui.UINewContactResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import java.util.*

class UIContactsServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String
) : UIContactsService {
    private val contactClient = ContactAsyncClient(serverUrl)
    private val contactListClient = ContactListAsyncClient(serverUrl)

    private val contactListSyncListeners = ArrayList<(Boolean) -> Unit>()
    private var isContactListSyncing = false

    init {
        app.contactListSyncing.subscribe { updateContactListSyncing(it) }
    }

    private fun updateContactListSyncing(value: Boolean) {
        isContactListSyncing = value
        for (listener in contactListSyncListeners)
            listener(value)
    }

    override fun addContactListSyncListener(listener: (Boolean) -> Unit) {
        contactListSyncListeners.add(listener)
        listener(isContactListSyncing)
    }

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
        val contactInfo = ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey)

        val authToken = app.userComponent?.userLoginData?.authToken ?: return Promise.ofFail(RuntimeException("Not logged in"))
        val keyVault = app.userComponent?.userLoginData?.keyVault ?: return Promise.ofFail(RuntimeException("Not logged in"))

        val remoteContactEntries = encryptRemoteContactEntries(keyVault, listOf(contactDetails.email))

        return contactListClient.addContacts(AddContactsRequest(authToken, remoteContactEntries)) bind {
            contactsPersistenceManager.add(contactInfo) map {
                contactDetails
            }
        }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()

        val authToken = app.userComponent?.userLoginData?.authToken ?: return Promise.ofFail(RuntimeException("Not logged in"))
        val keyVault = app.userComponent?.userLoginData?.keyVault ?: return Promise.ofFail(RuntimeException("Not logged in"))
        val remoteContactEntries = encryptRemoteContactEntries(keyVault, listOf(contactDetails.email)).map { it.hash }

        return contactListClient.removeContacts(RemoveContactsRequest(authToken, remoteContactEntries)) bind {
            contactsPersistenceManager.remove(ContactInfo(contactDetails.email, contactDetails.name, contactDetails.phoneNumber, contactDetails.publicKey))
        }
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
                UINewContactResult(true, null, UIContactDetails(contactInfo.name, contactInfo.phoneNumber, contactInfo.email, contactInfo.publicKey))
            }
        }
    }
}