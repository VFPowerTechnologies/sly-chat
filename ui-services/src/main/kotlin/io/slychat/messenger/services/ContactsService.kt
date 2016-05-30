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

class ContactJobDesc {
    var unadded: Boolean = false
        private set

    var updateRemote: Boolean = false
        private set

    var localSync: Boolean = false
        private set

    var remoteSync: Boolean = false
        private set

    fun localSync(): ContactJobDesc {
        unadded = true
        localSync = true
        updateRemote = true
        return this
    }

    fun remoteSync(): ContactJobDesc {
        unadded = true
        localSync = true
        updateRemote = true
        remoteSync = true
        return this
    }

    fun processUnadded(): ContactJobDesc {
        unadded = true
        updateRemote = true
        return this
    }

    fun updateRemote(): ContactJobDesc {
        //while not strictly necessary, might as well
        unadded = true
        updateRemote = true
        return this
    }
}

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

    private val log = LoggerFactory.getLogger(javaClass)

    private var isNetworkAvailable = false

    var isContactSyncActive = false
        private set

    private val contactClient = ContactAsyncClient(serverUrl)

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()
    val contactEvents: Observable<ContactEvent> = contactEventsSubject

    init {
        application.networkAvailable.subscribe { onNetworkStatusChange(it) }
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

    //add a new non-pending contact for which we already have info (from the ui's add new contact dialog)
    fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactsPersistenceManager.add(contactInfo) successUi { wasAdded ->
            if (wasAdded) {
                withCurrentJob { updateRemote() }
                contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
            }
        }
    }

    fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactsPersistenceManager.remove(contactInfo) successUi { wasRemoved ->
            if (wasRemoved) {
                withCurrentJob { updateRemote() }
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
    fun processPendingContacts() {
        withCurrentJob { processUnadded() }
    }

    fun remoteSync() {
        withCurrentJob { remoteSync() }
    }

    fun localSync() {
        withCurrentJob { localSync() }
    }

    //this will be called on network up
    //fetch+add contacts in pending state (behavior depends on ContactAddPolicy)
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

    private fun updateRemote(): Promise<Unit, Exception> {
        log.info("Beginning remote contact list update")
        return contactsPersistenceManager.getRemoteUpdates() bind { updates ->
            val client = ContactListAsyncClient(serverUrl)

            //FIXME add api to do both at once
            val adds = updates.filter { it.type == RemoteContactUpdateType.ADD }.map { it.userId }
            val removes = updates.filter { it.type == RemoteContactUpdateType.REMOVE }.map { it.userId }

            val keyVault = userLoginData.keyVault

            authTokenManager.bind { userCredentials ->
                val remoteContactEntries = encryptRemoteContactEntries(keyVault, adds)
                val request = AddContactsRequest(remoteContactEntries)
                client.addContacts(userCredentials, request)
            } bind {
                contactsPersistenceManager.removeRemoteUpdates(updates)
            }
        }
    }

    //TODO
    fun processContactRequestResponse(response: ContactRequestResponse) {
        log.debug("TODO: processContactRequestResponse")
    }

    private var currentRunningJob: Promise<Unit, Exception>? = null
    private var queuedJob: ContactJobDesc? = null

    private fun runJob(jobDesc: ContactJobDesc): Promise<Unit, Exception> {
        val jobRunners = ArrayList<(Unit) -> Promise<Unit, Exception>>()

        if (jobDesc.unadded)
            jobRunners.add { processUnadded() }

        if (jobDesc.localSync)
            jobRunners.add { syncLocalContacts() }

        if (jobDesc.updateRemote)
            jobRunners.add { updateRemote() }

        if (jobDesc.remoteSync)
            jobRunners.add {
                contactEventsSubject.onNext(ContactEvent.Sync(true))
                syncRemoteContactsList() alwaysUi {
                    contactEventsSubject.onNext(ContactEvent.Sync(false))
                }
            }

        val job = jobRunners.fold(Promise.ofSuccess<Unit, Exception>(Unit)) { z, v ->
            z bindUi v
        }

        return job
    }

    private fun processJob() {
        if (currentRunningJob != null)
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

    private fun nextJob() {
        currentRunningJob = null
        processJob()
    }

    private fun withCurrentJob(body: ContactJobDesc.() -> Unit) {
        val queuedJob = this.queuedJob
        val job = if (queuedJob != null)
            queuedJob
        else {
            val desc = ContactJobDesc()
            this.queuedJob = desc
            desc
        }

        job.body()

        processJob()
    }
}