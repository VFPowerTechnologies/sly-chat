package io.slychat.messenger.services

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.RemoteContactUpdateType
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

enum class ContactAddPolicy {
    AUTO,
    ASK,
    REJECT
}

data class ContactRequestResponse(
    val responses: Map<ContactInfo, Boolean>
)

class ContactsService(
    private val authTokenManager: AuthTokenManager,
    private val serverUrl: String,
    private val application: SlyApplication,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val userLoginData: UserData,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager,
    private val platformContacts: PlatformContacts,
    //update in regards to config changes?
    private var contactAddPolicy: ContactAddPolicy = ContactAddPolicy.AUTO
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isNetworkAvailable = false

    var isContactSyncActive = false
        private set

    private val contactClient = ContactAsyncClient(serverUrl)
    private val contactListClient = ContactListAsyncClient(serverUrl)

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()

    val contactEvents: Observable<ContactEvent> = contactEventsSubject

    private var currentRunningJob: Promise<Unit, Exception>? = null
    private var queuedJob: ContactJobDescription? = null

    init {
        application.networkAvailable.subscribe { onNetworkStatusChange(it) }
    }

    private fun getDefaultRegionCode(): Promise<String, Exception> {
        return accountInfoPersistenceManager.retrieve() map { getAccountRegionCode(it!!) }
    }

    /** Attempts to find any registered users matching the user's local contacts. */
    private fun syncLocalContacts(): Promise<Unit, Exception> {
        log.info("Beginning local contact sync")

        return getDefaultRegionCode() bind { defaultRegion ->
            authTokenManager.bind { userCredentials ->
                platformContacts.fetchContacts() map { contacts ->
                    val phoneNumberUtil = PhoneNumberUtil.getInstance()

                    val updated = contacts.map { contact ->
                        val phoneNumbers = contact.phoneNumbers
                            .map { parsePhoneNumber(it, defaultRegion) }
                            .filter { it != null }
                            .map { phoneNumberUtil.format(it, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1) }
                        contact.copy(phoneNumbers = phoneNumbers)
                    }

                    log.debug("Platform contacts: {}", updated)

                    updated
                } bind { contacts ->
                    contactsPersistenceManager.findMissing(contacts)
                } bind { missingContacts ->
                    log.debug("Missing local contacts:", missingContacts)
                    val client = ContactAsyncClient(serverUrl)
                    client.findLocalContacts(userCredentials, FindLocalContactsRequest(missingContacts))
                } bind { foundContacts ->
                    log.debug("Found local contacts: {}", foundContacts)

                    contactsPersistenceManager.addAll(foundContacts.contacts.map { it.toCore(false) }) map { Unit }
                }
            }
        }
    }

    /** Syncs the local contact list with the remote contact list. */
    private fun syncRemoteContactsList(): Promise<Unit, Exception> {
        log.debug("Beginning remote contact list sync")

        val client = ContactListAsyncClient(serverUrl)

        val keyVault = userLoginData.keyVault

        val contactsPersistenceManager = contactsPersistenceManager

        return authTokenManager.bind { userCredentials ->
            client.getContacts(userCredentials) bind { response ->
                val emails = decryptRemoteContactEntries(keyVault, response.contacts)
                contactsPersistenceManager.getDiff(emails) bind { diff ->
                    log.debug("New contacts: {}", diff.newContacts)
                    log.debug("Removed contacts: {}", diff.removedContacts)

                    val contactsClient = ContactAsyncClient(serverUrl)
                    val request = FetchContactInfoByIdRequest(diff.newContacts.toList())
                    contactsClient.fetchContactInfoById(userCredentials, request) bind { response ->
                        val newContacts = response.contacts.map { it.toCore(false) }
                        contactsPersistenceManager.applyDiff(newContacts, diff.removedContacts.toList())
                    }
                }
            }
        }
    }

    private fun processUnadded(): Promise<Unit, Exception> {
        log.info("Processing unadded contacts")

        return contactsPersistenceManager.getUnadded() bind { users ->
            addPendingContacts(users)
        } fail { e ->
            log.error("Failed to fetch unadded users on startup")
        }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isAvailable)
            processJob()
    }

    /** Add a new non-pending contact for which we already have info. */
    fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactsPersistenceManager.add(contactInfo) successUi { wasAdded ->
            if (wasAdded) {
                withCurrentJob { doUpdateRemoteContactList() }
                contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
            }
        }
    }

    /** Remove the given contact from the contact list. */
    fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactsPersistenceManager.remove(contactInfo) successUi { wasRemoved ->
            if (wasRemoved) {
                withCurrentJob { doUpdateRemoteContactList() }
                contactEventsSubject.onNext(ContactEvent.Removed(setOf(contactInfo)))
            }
        }
    }

    //we want to keep the policy we had when we started processing
    private fun handleContactLookupResponse(policy: ContactAddPolicy, users: Set<UserId>, response: FetchContactInfoByIdResponse): Promise<Unit, Exception> {
        val foundIds = response.contacts.mapTo(HashSet()) { it.id }

        val missing = HashSet(users)
        missing.removeAll(foundIds)

        //XXX blacklist? at least temporarily or something
        if (missing.isNotEmpty())
            contactEventsSubject.onNext(ContactEvent.InvalidContacts(missing))

        val isPending = if (policy == ContactAddPolicy.AUTO) false else true

        val contacts = response.contacts.map { it.toCore(isPending) }

        return contactsPersistenceManager.addAll(contacts) mapUi { newContacts ->
            if (newContacts.isNotEmpty()) {
                val ev = if (policy == ContactAddPolicy.AUTO)
                    ContactEvent.Added(newContacts)
                else
                    ContactEvent.Request(newContacts)

                contactEventsSubject.onNext(ev)
            }
        } fail { e ->
            log.error("Unable to add new contacts: {}", e.message, e)
        }
    }

    /** Filter out users whose messages we should ignore. */
    //in the future, this will also check for blocked/deleted users
    fun allowMessagesFrom(users: Set<UserId>): Promise<Set<UserId>, Exception> {
        //avoid errors if the caller modifiers the set after giving it
        val usersCopy = HashSet(users)
        return when (contactAddPolicy) {
            ContactAddPolicy.REJECT -> contactsPersistenceManager.exists(usersCopy)
            else -> Promise.ofSuccess(usersCopy)
        }
    }

    //called from MessengerService
    fun doProcessUnaddedContacts() {
        withCurrentJob { doProcessUnaddedContacts() }
    }

    fun doRemoteSync() {
        withCurrentJob { doRemoteSync() }
    }

    fun doLocalSync() {
        withCurrentJob { doLocalSync() }
    }

    /** Process the given unadded users. */
    private fun addPendingContacts(users: Set<UserId>): Promise<Unit, Exception> {
        if (users.isEmpty())
            return Promise.ofSuccess(Unit)

        //ignore messages from people in the contact list
        if (contactAddPolicy == ContactAddPolicy.REJECT)
            return Promise.ofSuccess(Unit)

        return authTokenManager.bind { userCredentials ->
            val request = FetchContactInfoByIdRequest(users.toList())
            contactClient.fetchContactInfoById(userCredentials, request)
        } bindUi { response ->
            handleContactLookupResponse(contactAddPolicy, users, response)
        } fail { e ->
            //the only recoverable error would be a network error; when the network is restored, this'll get called again
            log.error("Unable to fetch contact info: {}", e.message, e)
        }
    }

    private fun updateRemoteContactList(): Promise<Unit, Exception> {
        log.info("Beginning remote contact list update")

        return contactsPersistenceManager.getRemoteUpdates() bind { updates ->
            val adds = updates.filter { it.type == RemoteContactUpdateType.ADD }.map { it.userId }
            val removes = updates.filter { it.type == RemoteContactUpdateType.REMOVE }.map { it.userId }

            if (adds.isEmpty() && removes.isEmpty()) {
                log.info("No new contacts")
                Promise.ofSuccess(Unit)
            }
            else {
                log.info("Remote update: add={}; remove={}", adds.map { it.long }, removes.map { it.long })

                val keyVault = userLoginData.keyVault

                authTokenManager.bind { userCredentials ->
                    val request = updateRequestFromContactInfo(keyVault, adds, removes)
                    contactListClient.updateContacts(userCredentials, request)
                } bind {
                    contactsPersistenceManager.removeRemoteUpdates(updates)
                }
            }
        }
    }

    //TODO
    fun processContactRequestResponse(response: ContactRequestResponse) {
        log.debug("TODO: processContactRequestResponse")
    }

    /** Create and run a job with the given job description. */
    private fun runJob(jobDescription: ContactJobDescription): Promise<Unit, Exception> {
        val jobRunners = ArrayList<(Unit) -> Promise<Unit, Exception>>()

        if (jobDescription.unadded)
            jobRunners.add { processUnadded() }

        if (jobDescription.localSync)
            jobRunners.add { syncLocalContacts() }

        if (jobDescription.updateRemote)
            jobRunners.add { updateRemoteContactList() }

        if (jobDescription.remoteSync)
            jobRunners.add {
                isContactSyncActive = true
                contactEventsSubject.onNext(ContactEvent.Sync(true))

                syncRemoteContactsList() alwaysUi {
                    isContactSyncActive = false
                    contactEventsSubject.onNext(ContactEvent.Sync(false))
                }
            }

        val job = jobRunners.fold(Promise.ofSuccess<Unit, Exception>(Unit)) { z, v ->
            z bindUi v
        }

        return job
    }

    /** Process the next queued job if no job is currently running and the network is active. */
    private fun processJob() {
        if (currentRunningJob != null || !isNetworkAvailable)
            return

        val queuedJob = this.queuedJob ?: return

        val job = runJob(queuedJob)

        currentRunningJob = job
        this.queuedJob = null

        job success {
            log.info("Contact job completed successfully")
        } fail { e ->
            log.error("Contact job failed: {}", e.message, e)
        } alwaysUi {
            nextJob()
        }
    }

    /** Process the next queued job, if any. */
    private fun nextJob() {
        currentRunningJob = null
        processJob()
    }

    /** Used to mark job components for execution. */
    private fun withCurrentJob(body: ContactJobDescription.() -> Unit) {
        val queuedJob = this.queuedJob
        val job = if (queuedJob != null)
            queuedJob
        else {
            val desc = ContactJobDescription()
            this.queuedJob = desc
            desc
        }

        job.body()

        processJob()
    }
}