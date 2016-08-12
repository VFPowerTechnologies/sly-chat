package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.formatPhoneNumber
import io.slychat.messenger.services.getAccountRegionCode
import io.slychat.messenger.services.parsePhoneNumber
import io.slychat.messenger.services.ui.UIContactDetails
import io.slychat.messenger.services.ui.UIContactEvent
import io.slychat.messenger.services.ui.UIContactsService
import io.slychat.messenger.services.ui.UINewContactResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Observable
import rx.Subscription
import java.util.*

class UIContactsServiceImpl(
    userSessionAvailable: Observable<UserComponent?>
) : UIContactsService {
    private var contactEventSub: Subscription? = null
    private val contactEventListeners = ArrayList<(UIContactEvent) -> Unit>()

    private var isContactSyncActive = false

    private var contactsService: ContactsService? = null
    private var contactsPersistenceManager: ContactsPersistenceManager? = null
    private var accountInfoSubscription: Subscription? = null

    private var currentRegionCode: String? = null

    init {
        userSessionAvailable.subscribe {
            if (it == null) {
                contactEventSub?.unsubscribe()
                contactEventSub = null

                accountInfoSubscription?.unsubscribe()
                accountInfoSubscription = null

                contactsService = null
                contactsPersistenceManager = null

                currentRegionCode = null
            }
            else {
                contactsService = it.contactsService
                contactsPersistenceManager = it.contactsPersistenceManager

                contactEventSub = it.contactsService.contactEvents.subscribe { onContactEvent(it) }

                it.accountInfoManager.accountInfo.subscribe { onAccountInfoUpdate(it) }
            }
        }
    }

    private fun onAccountInfoUpdate(accountInfo: AccountInfo) {
        currentRegionCode = getAccountRegionCode(accountInfo)
    }

    private fun getContactsServiceOrThrow(): ContactsService {
        return contactsService ?: throw IllegalStateException("Not logged in")
    }

    private fun onContactEvent(event: ContactEvent) {
        val ev = when (event) {
            is ContactEvent.Added ->
                UIContactEvent.Added(event.contacts.toUI())

            is ContactEvent.Removed ->
                UIContactEvent.Removed(event.contacts.toUI())

            is ContactEvent.Updated ->
                UIContactEvent.Updated(event.contacts.toUI())

            is ContactEvent.Sync -> {
                isContactSyncActive = event.isRunning
                UIContactEvent.Sync(event.isRunning)
            }

            else -> null
        }

        if (ev != null)
            contactEventListeners.forEach { it(ev) }
    }

    override fun addContactEventListener(listener: (UIContactEvent) -> Unit) {
        contactEventListeners.add(listener)

        //replay any status events
        listener(UIContactEvent.Sync(isContactSyncActive))
    }

    private fun getContactsPersistenceManagerOrThrow(): ContactsPersistenceManager =
        contactsPersistenceManager ?: error("Not logged in")

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
        val defaultRegionCode = currentRegionCode ?: error("No account info available")

        if (email == null && phoneNumber == null) {
            return Promise.ofSuccess(UINewContactResult(false, "Username or phone number must be provided", null))
        }

        //PhoneNumberUtil.parse actually accepts letters, but since this tends to go unused anyways, we just reject
        //phone numbers with letters in them
        if (phoneNumber != null) {
            if (phoneNumber.any { it.isLetter() })
                return Promise.ofSuccess(UINewContactResult(false, "Not a valid phone number", null))
        }

        val queryPhoneNumber = if (phoneNumber != null) {
            val p = parsePhoneNumber(phoneNumber, defaultRegionCode)
            if (p != null) formatPhoneNumber(p) else null
        }
        else
            null

        if (phoneNumber != null && queryPhoneNumber == null)
            return Promise.ofSuccess(UINewContactResult(false, "Not a valid phone number", null))

        return getContactsServiceOrThrow().fetchRemoteContactInfo(email, queryPhoneNumber) map { response ->
            if (response.errorMessage != null) {
                UINewContactResult(false, response.errorMessage, null)
            } else {
                val contactInfo = response.contactInfo!!
                UINewContactResult(true, null, contactInfo.toUI())
            }
        }
    }
}