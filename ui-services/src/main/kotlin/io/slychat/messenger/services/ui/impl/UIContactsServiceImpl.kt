package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.NewContactRequest
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.*
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIContactDetails
import io.slychat.messenger.services.ui.UIContactEvent
import io.slychat.messenger.services.ui.UIContactsService
import io.slychat.messenger.services.ui.UINewContactResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Subscription
import java.util.*

class UIContactsServiceImpl(
    private val app: SlyApplication,
    serverUrl: String
) : UIContactsService {

    private val contactClient = ContactAsyncClient(serverUrl)

    private var contactEventSub: Subscription? = null
    private val contactEventListeners = ArrayList<(UIContactEvent) -> Unit>()

    init {
        app.userSessionAvailable.subscribe { isAvailable ->
            if (!isAvailable) {
                contactEventSub?.unsubscribe()
                contactEventSub = null
            }
            else {
                contactEventSub = getContactsServiceOrThrow().contactEvents.subscribe { onContactEvent(it) }
            }
        }
    }

    private fun getContactsServiceOrThrow(): ContactsService {
        return app.userComponent?.contactsService ?: throw IllegalStateException("Not logged in")
    }

    private fun onContactEvent(event: ContactEvent) {
        val ev = when (event) {
            is ContactEvent.Added ->
                UIContactEvent.Added(event.contacts.toUI())

            is ContactEvent.Removed ->
                UIContactEvent.Removed(event.contacts.toUI())

            is ContactEvent.Updated ->
                UIContactEvent.Updated(event.contacts.toUI())

            is ContactEvent.Request ->
                UIContactEvent.Request(event.contacts.toUI())

            is ContactEvent.Sync ->
                UIContactEvent.Sync(event.isRunning)

            else -> null
        }

        if (ev != null)
            contactEventListeners.forEach { it(ev) }
    }

    private fun getUserComponentOrThrow(): UserComponent {
        return app.userComponent ?: throw IllegalStateException("Not logged in")
    }

    override fun addContactEventListener(listener: (UIContactEvent) -> Unit) {
        contactEventListeners.add(listener)

        val contactsService = getContactsServiceOrThrow()

        //replay any status events
        listener(UIContactEvent.Sync(contactsService.isContactSyncActive))
    }

    private fun getContactsPersistenceManagerOrThrow(): ContactsPersistenceManager =
        app.userComponent?.contactsPersistenceManager ?: error("No UserComponent available")

    override fun updateContact(newContactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        val contactsService = getContactsServiceOrThrow()
        return contactsService.updateContact(newContactDetails.toNative()) map { newContactDetails }
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.getAll() map { contacts ->
            contacts.toUI()
        }
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        val contactInfo = contactDetails.toNative()

        val contactsService = getContactsServiceOrThrow()

        return contactsService.addContact(contactInfo) map { contactDetails }
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        val contactInfo = contactDetails.toNative()

        val contactsService = getContactsServiceOrThrow()

        return contactsService.removeContact(contactInfo) map { Unit }
    }

    override fun fetchNewContactInfo(email: String?, phoneNumber: String?): Promise<UINewContactResult, Exception> {
        if (email == null && phoneNumber == null) {
            return Promise.ofSuccess(UINewContactResult(false, "Username or phone number must be provided", null))
        }

        //PhoneNumberUtil.parse actually accepts letters, but since this tends to go unused anyways, we just reject
        //phone numbers with letters in them
        if (phoneNumber != null) {
            if (phoneNumber.any { it.isLetter() })
                return Promise.ofSuccess(UINewContactResult(false, "Not a valid phone number", null))
        }

        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { userCredentials ->
            val queryPhoneNumber = if (phoneNumber != null) {
                val accountInfo = userComponent.accountInfoPersistenceManager.retrieveSync()!!
                val defaultRegionCode = getAccountRegionCode(accountInfo)
                val p = parsePhoneNumber(phoneNumber, defaultRegionCode)
                if (p != null) formatPhoneNumber(p) else null
            }
            else
                null

            if (phoneNumber != null && queryPhoneNumber == null)
                Promise.ofSuccess(UINewContactResult(false, "Not a valid phone number", null))
            else {
                val request = NewContactRequest(email, queryPhoneNumber)

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
}