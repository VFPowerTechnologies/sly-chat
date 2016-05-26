package io.slychat.messenger.services

import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.FetchContactInfoByIdRequest
import io.slychat.messenger.core.http.api.contacts.FetchContactInfoByIdResponse
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
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
    private val serverUrls: BuildConfig.ServerUrls,
    private val application: SlyApplication,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val contactSyncManager: ContactSyncManager,
    //update in regards to config changes?
    private var contactAddPolicy: ContactAddPolicy = ContactAddPolicy.AUTO
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isNetworkAvailable = false

    private var isContactSyncActive = false

    private val contactClient = ContactAsyncClient(serverUrls.API_SERVER)

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()
    val contactEvents: Observable<ContactEvent> = contactEventsSubject

    init {
        application.networkAvailable.subscribe { onNetworkStatusChange(it) }
        contactSyncManager.status.subscribe {
            isContactSyncActive = it
            if (!isContactSyncActive)
                processUnadded()
        }
    }

    private fun processUnadded() {
        contactsPersistenceManager.getUnadded() successUi { users ->
            addPendingContacts(users)
        } fail { e ->
            log.error("Failed to fetch unadded users on startup")
        }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (isAvailable)
            processUnadded()
    }

    //add a new non-pending contact for which we already have info (from the ui's add new contact dialog)
    fun addContact(contactInfo: ContactInfo): Promise<Unit, Exception> {
        throw NotImplementedError("addContact")
    }

    //we want to keep the policy we had when we started processing
    private fun handleContactLookupResponse(policy: ContactAddPolicy, users: Set<UserId>, response: FetchContactInfoByIdResponse) {
        val foundIds = response.contacts.mapTo(HashSet()) { it.id }

        val missing = HashSet(users)
        missing.removeAll(foundIds)

        //XXX blacklist? at least temporarily or something
        if (missing.isNotEmpty())
            contactEventsSubject.onNext(ContactEvent.InvalidContacts(missing))

        val isPending = if (policy == ContactAddPolicy.AUTO) false else true

        val contacts = response.contacts.map { it.toCore(isPending) }

        contactsPersistenceManager.addAll(contacts) successUi { newContacts ->
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

    //this will be called on network up
    //fetch+add contacts in pending state (behavior depends on ContactAddPolicy)
    fun addPendingContacts(users: Set<UserId>) {
        if (!isNetworkAvailable || isContactSyncActive)
            return

        if (users.isEmpty())
            return

        //ignore messages from people in the contact list
        if (contactAddPolicy == ContactAddPolicy.REJECT)
            return

        authTokenManager.bind { userCredentials ->
            val request = FetchContactInfoByIdRequest(users.toList())
            contactClient.fetchContactInfoById(userCredentials, request)
        } successUi { response ->
            handleContactLookupResponse(contactAddPolicy, users, response)
        } fail { e ->
            //the only recoverable error would be a network error; when the network is restored, this'll get called again
            log.error("Unable to fetch contact info: {}", e.message, e)
        }
    }

    //TODO
    fun processContactRequestResponse(response: ContactRequestResponse) {
        log.debug("TODO: processContactRequestResponse")
    }
}