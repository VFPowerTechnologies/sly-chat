package com.vfpowertech.keytap.services

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.vfpowertech.keytap.core.http.api.contacts.*
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.alwaysUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class ContactSyncManager(
    private val application: KeyTapApplication,
    private val userLoginData: UserLoginData,
    private val accountInfo: AccountInfo,
    private val serverUrl: String,
    private val platformContacts: PlatformContacts,
    private val contactsPersistenceManager: ContactsPersistenceManager
) {
    companion object {
        private enum class ContactSyncType {
            LOCAL,
            FULL
        }
    }

    private val runningSubject = BehaviorSubject.create(false)
    val status: Observable<Boolean> = runningSubject

    private val log = LoggerFactory.getLogger(javaClass)

    //this is the next type of request to process
    //if we're waiting to run, this just gets upgraded/etc
    //if we're running, this is null, so it'll get set to the next type of request to process
    //we need to allow for queuing multiple request, since if a full sync is running, then the user updates his local
    //contacts during that process, a local sync needs to be performed afterwards
    private var scheduled: ContactSyncType? = null

    private var running = false

    private var isOnline = false

    init {
        application.networkAvailable.subscribe { status ->
            isOnline = status
            if (status)
                processRequest()
        }
    }

    /** Syncs the local contact list with the remote contact list. */
    private fun syncRemoteContactsList(): Promise<Unit, Exception> {
        log.debug("Beginning remote contact list sync")

        val client = ContactListAsyncClient(serverUrl)

        val keyVault = userLoginData.keyVault
        val authToken = userLoginData.authToken
        if (authToken == null) {
            log.debug("authToken is null, aborting remote contacts sync")
            return Promise.ofFail(RuntimeException("Null authToken"))
        }

        val contactsPersistenceManager = contactsPersistenceManager

        return client.getContacts(GetContactsRequest(authToken)) bind { response ->
            val emails = decryptRemoteContactEntries(keyVault, response.contacts)
            contactsPersistenceManager.getDiff(emails) bind { diff ->
                log.debug("New contacts: {}", diff.newContacts)
                log.debug("Removed contacts: {}", diff.removedContacts)

                val contactsClient = ContactAsyncClient(serverUrl)
                val request = FetchContactInfoByIdRequest(authToken, diff.newContacts.toList())
                contactsClient.fetchContactInfoByEmail(request) bind { response ->
                    contactsPersistenceManager.applyDiff(response.contacts, diff.removedContacts.toList())
                }
            }
        }
    }

    /** Attempts to find any registered users matching the user's local contacts. */
    private fun syncLocalContacts(): Promise<Unit, Exception> {
        val authToken = userLoginData.authToken
        if (authToken == null) {
            log.debug("authToken is null, aborting local contacts sync")
            return Promise.ofFail(RuntimeException("Null authToken"))
        }

        val keyVault = userLoginData.keyVault

        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val phoneNumber = phoneNumberUtil.parse("+${accountInfo.phoneNumber}", null)
        val defaultRegion = phoneNumberUtil.getRegionCodeForCountryCode(phoneNumber.countryCode)

        return platformContacts.fetchContacts() map { contacts ->
            val phoneNumberUtil = PhoneNumberUtil.getInstance()

            val updated = contacts.map { contact ->
                val phoneNumbers = contact.phoneNumbers.map { parsePhoneNumber(it, defaultRegion) }.filter { it != null }.map { phoneNumberUtil.format(it, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1) }
                contact.copy(phoneNumbers = phoneNumbers)
            }

            log.debug("Platform contacts: {}", updated)

            updated
        } bind { contacts ->
            contactsPersistenceManager.findMissing(contacts)
        } bind { missingContacts ->
            log.debug("Missing local contacts:", missingContacts)
            val client = ContactAsyncClient(serverUrl)
            client.findLocalContacts(FindLocalContactsRequest(authToken, missingContacts))
        } bind { foundContacts ->
            log.debug("Found local contacts: {}", foundContacts)

            val client = ContactListAsyncClient(serverUrl)
            val remoteContactEntries = encryptRemoteContactEntries(keyVault, foundContacts.contacts.map { it.id })
            val request = AddContactsRequest(authToken, remoteContactEntries)

            client.addContacts(request) bind {
                contactsPersistenceManager.addAll(foundContacts.contacts.map { ContactInfo(it.id, it.email, it.name, it.phoneNumber, it.publicKey) })
            }
        } fail { e ->
            log.error("Local contacts sync failed: {}", e.message, e)
        }
    }

    private fun processRequest() {
        if (!isOnline)
            return

        if (running)
            return

        val type = scheduled ?: return

        scheduled = null
        running = true

        runningSubject.onNext(true)

        log.debug("Scheduled contacts sync type: $type")

        val p = when (type) {
            ContactSyncType.LOCAL -> {
                syncLocalContacts()
            }

            ContactSyncType.FULL -> {
                syncRemoteContactsList() bind {
                    syncLocalContacts()
                }
            }
        }

        p fail { e ->
            log.error("Contacts syncing failed: {}", e.message, e)
        } alwaysUi {
            running = false
            runningSubject.onNext(false)
            //incase we have another request queued
            processRequest()
        }
    }

    fun localSync() {
        //else we're either a full or already local
        if (scheduled == null)
            scheduled = ContactSyncType.LOCAL

        processRequest()
    }

    fun fullSync() {
        //full includes local, so if it's local just upgrade it
        scheduled = ContactSyncType.FULL

        processRequest()
    }
}