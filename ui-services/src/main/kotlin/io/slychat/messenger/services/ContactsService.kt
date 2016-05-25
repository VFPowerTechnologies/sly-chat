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
    //update in regards to config changes?
    private var contactAddPolicy: ContactAddPolicy = ContactAddPolicy.AUTO
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val contactClient = ContactAsyncClient(serverUrls.API_SERVER)

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()
    val contactEvents: Observable<ContactEvent> = contactEventsSubject

    private var isNetworkAvailable = false

    private val pendingLookup = HashSet<UserId>()

    init {
        //TODO
        //application.networkAvailable.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(available: Boolean) {
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

    //fetch+add contacts in pending state (behavior depends on ContactAddPolicy)
    fun addPendingContacts(users: Set<UserId>) {
        //ignore messages from people in the contact list
        if (contactAddPolicy == ContactAddPolicy.REJECT)
            return

        //FIXME need to queue when network is offline or something
        //also don't run more than one request a time?
        //if a user keeps sending message we could end up requesting his info multiple times
        authTokenManager.bind { userCredentials ->
            val request = FetchContactInfoByIdRequest(users.toList())
            contactClient.fetchContactInfoById(userCredentials, request)
        } successUi { response ->
            handleContactLookupResponse(contactAddPolicy, users, response)
        } fail { e ->
            //XXX if this fails, if recoverable (connection error), reschedule
            log.error("Unable to fetch contact info: {}", e.message, e)
        }
    }

    //TODO
    fun processContactRequestResponse(response: ContactRequestResponse) {
        log.debug("TODO: processContactRequestResponse")
    }
}