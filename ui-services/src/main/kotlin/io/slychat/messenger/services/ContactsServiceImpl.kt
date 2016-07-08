package io.slychat.messenger.services

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.promiseOnUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.*

class ContactsServiceImpl(
    private val authTokenManager: AuthTokenManager,
    networkAvailable: Observable<Boolean>,
    private val contactClient: ContactAsyncClient,
    private val contactListClient: ContactListAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val userLoginData: UserData,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager,
    private val platformContacts: PlatformContacts
) : ContactsService {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isNetworkAvailable = false

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()

    override val contactEvents: Observable<ContactEvent> = contactEventsSubject

    private var currentRunningJob: Promise<Unit, Exception>? = null
    private var queuedJob: ContactJobDescription? = null

    private val networkAvailableSubscription: Subscription

    init {
        networkAvailableSubscription = networkAvailable.subscribe { onNetworkStatusChange(it) }
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
                    contactClient.findLocalContacts(userCredentials, FindLocalContactsRequest(missingContacts))
                } bind { foundContacts ->
                    log.debug("Found local contacts: {}", foundContacts)

                    contactsPersistenceManager.add(foundContacts.contacts.map { it.toCore(false, AllowedMessageLevel.ALL) }) map { Unit }
                }
            }
        }
    }

    /** Syncs the local contact list with the remote contact list. */
    private fun syncRemoteContactsList(): Promise<Unit, Exception> {
        log.debug("Beginning remote contact list sync")

        val keyVault = userLoginData.keyVault

        val contactsPersistenceManager = contactsPersistenceManager

        return authTokenManager.bind { userCredentials ->
            contactListClient.getContacts(userCredentials) bind { response ->
                val emails = decryptRemoteContactEntries(keyVault, response.contacts)
                contactsPersistenceManager.getDiff(emails) bind { diff ->
                    log.debug("New contacts: {}", diff.newContacts)
                    log.debug("Removed contacts: {}", diff.removedContacts)

                    val request = FetchContactInfoByIdRequest(diff.newContacts.toList())
                    contactClient.fetchContactInfoById(userCredentials, request) bind { response ->
                        val newContacts = response.contacts.map { it.toCore(false, AllowedMessageLevel.ALL) }
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
            log.error("Failed to fetch unadded users on startup: {}", e.message, e)
        }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isAvailable)
            processJob()
    }

    override fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactsPersistenceManager.add(contactInfo) successUi { wasAdded ->
            if (wasAdded) {
                withCurrentJob { doUpdateRemoteContactList() }
                contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
            }
        }
    }

    /** Remove the given contact from the contact list. */
    override fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactsPersistenceManager.remove(contactInfo) successUi { wasRemoved ->
            if (wasRemoved) {
                withCurrentJob { doUpdateRemoteContactList() }
                contactEventsSubject.onNext(ContactEvent.Removed(setOf(contactInfo)))
            }
        }
    }

    override fun updateContact(contactInfo: ContactInfo): Promise<Unit, Exception> {
        return contactsPersistenceManager.update(contactInfo) successUi {
            contactEventsSubject.onNext(ContactEvent.Updated(setOf(contactInfo)))
        }
    }

    private fun handleContactLookupResponse(users: Set<UserId>, response: FetchContactInfoByIdResponse): Promise<Unit, Exception> {
        val foundIds = response.contacts.mapTo(HashSet()) { it.id }

        val missing = HashSet(users)
        missing.removeAll(foundIds)

        //XXX blacklist? at least temporarily or something
        if (missing.isNotEmpty())
            contactEventsSubject.onNext(ContactEvent.InvalidContacts(missing))

        val contacts = response.contacts.map { it.toCore(true, AllowedMessageLevel.GROUP_ONLY) }

        return contactsPersistenceManager.add(contacts) mapUi { newContacts ->
            if (newContacts.isNotEmpty()) {
                val ev = ContactEvent.Added(newContacts)

                contactEventsSubject.onNext(ev)
            }
        } fail { e ->
            log.error("Unable to add new contacts: {}", e.message, e)
        }
    }

    /** Filter out users whose messages we should ignore. */
    //in the future, this will also check for blocked/deleted users
    override fun allowMessagesFrom(users: Set<UserId>): Promise<Set<UserId>, Exception> {
        //avoid errors if the caller modifiers the set after giving it
        val usersCopy = HashSet(users)
        return contactsPersistenceManager.filterBlocked(usersCopy)
    }

    //called from MessengerService
    override fun doProcessUnaddedContacts() {
        withCurrentJob { doProcessUnaddedContacts() }
    }

    override fun doRemoteSync() {
        withCurrentJob { doRemoteSync() }
    }

    override fun doLocalSync() {
        withCurrentJob { doLocalSync() }
    }

    /** Process the given unadded users. */
    private fun addPendingContacts(users: Set<UserId>): Promise<Unit, Exception> {
        if (users.isEmpty())
            return Promise.ofSuccess(Unit)

        return authTokenManager.bind { userCredentials ->
            val request = FetchContactInfoByIdRequest(users.toList())
            contactClient.fetchContactInfoById(userCredentials, request)
        } bindUi { response ->
            handleContactLookupResponse(users, response)
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
                syncRemoteContactsList()
            }

        //if remote sync is at all enabled, we want the entire process to lock down the contact list
        val initial = if (jobDescription.remoteSync) {
            promiseOnUi {
                contactEventsSubject.onNext(ContactEvent.Sync(true))
            }
        }
        else
            Promise.ofSuccess<Unit, Exception>(Unit)

        val job = jobRunners.fold(initial) { z, v ->
            z bindUi v
        }

        if (jobDescription.remoteSync) {
            job alwaysUi {
                contactEventsSubject.onNext(ContactEvent.Sync(false))
            }
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

    override fun shutdown() {
        networkAvailableSubscription.unsubscribe()
    }

    //FIXME return an Either<String, ApiContactInfo>
    override fun fetchRemoteContactInfo(email: String?, queryPhoneNumber: String?): Promise<FetchContactResponse, Exception> {
        return authTokenManager.bind { userCredentials ->
            val request = NewContactRequest(email, queryPhoneNumber)

            contactClient.fetchNewContactInfo(userCredentials, request)
        }
    }
}