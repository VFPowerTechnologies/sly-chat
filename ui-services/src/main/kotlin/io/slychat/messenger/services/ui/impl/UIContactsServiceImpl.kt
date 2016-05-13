package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIContactDetails
import io.slychat.messenger.services.ui.UIContactsService
import io.slychat.messenger.services.ui.UINewContactResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import java.util.*

class UIContactsServiceImpl(
    private val app: SlyApplication,
    serverUrl: String
) : UIContactsService {
    private val contactClient = ContactAsyncClient(serverUrl)
    private val contactListClient = ContactListAsyncClient(serverUrl)

    private val contactListSyncListeners = ArrayList<(Boolean) -> Unit>()
    private var isContactListSyncing = false

    init {
        app.contactListSyncing.subscribe { updateContactListSyncing(it) }
    }

    private fun getUserComponentOrThrow(): UserComponent {
        return app.userComponent ?: throw IllegalStateException("Not logged in")
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
        return contactsPersistenceManager.update(newContactDetails.toNative()) map { newContactDetails }
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.getAll() map { contacts ->
            contacts.map { it.toUI() }
        }
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        val contactInfo = contactDetails.toNative()

        val userComponent = getUserComponentOrThrow()

        val keyVault = userComponent.userLoginData.keyVault

        val remoteContactEntries = encryptRemoteContactEntries(keyVault, listOf(contactDetails.id))

        return userComponent.authTokenManager.bind { userCredentials ->
            contactListClient.addContacts(userCredentials, AddContactsRequest(remoteContactEntries)) bind {
                contactsPersistenceManager.add(contactInfo) map {
                    contactDetails
                }
            }
        }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()

        val userComponent = getUserComponentOrThrow()

        val keyVault = userComponent.userLoginData.keyVault

        val remoteContactEntries = encryptRemoteContactEntries(keyVault, listOf(contactDetails.id)).map { it.hash }

        return userComponent.authTokenManager.bind { userCredentials ->
            contactListClient.removeContacts(userCredentials, RemoveContactsRequest(remoteContactEntries)) bind {
                contactsPersistenceManager.remove(contactDetails.toNative())
            }
        }
    }

    override fun fetchNewContactInfo(email: String?, phoneNumber: String?): Promise<UINewContactResult, Exception> {
        if (email == null && phoneNumber == null) {
            return Promise.ofSuccess(UINewContactResult(false, "Username or phone number must be provided", null))
        }

        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { userCredentials ->
            val request = NewContactRequest(email, phoneNumber)

            contactClient.fetchNewContactInfo(userCredentials, request) map { response ->
                if (response.errorMessage != null) {
                    UINewContactResult(false, response.errorMessage, null)
                } else {
                    val contactInfo = response.contactInfo!!
                    UINewContactResult(true, null, contactInfo.toUI())
                }
            }
        }
    }
}