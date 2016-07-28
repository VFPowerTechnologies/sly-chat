package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class ContactsServiceImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactClient: ContactAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val addressBookOperationManager: AddressBookOperationManager
) : ContactsService {
    private class AddContactsResult(
        val added: Boolean,
        val invalidIds: Set<UserId>
    )

    private val log = LoggerFactory.getLogger(javaClass)

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()

    override val contactEvents: Observable<ContactEvent> = contactEventsSubject

    init {
        addressBookOperationManager.running.subscribe { onContactSyncStatusUpdate(it) }
    }

    override fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.add(contactInfo)
        } successUi { wasAdded ->
            if (wasAdded) {
                withCurrentJob { doUpdateRemoteContactList() }
                contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
            }
        }
    }

    /** Remove the given contact from the contact list. */
    override fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.remove(contactInfo.id)
        } successUi { wasRemoved ->
            if (wasRemoved) {
                withCurrentJob { doUpdateRemoteContactList() }
                contactEventsSubject.onNext(ContactEvent.Removed(setOf(contactInfo)))
            }
        }
    }

    override fun updateContact(contactInfo: ContactInfo): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.update(contactInfo)
        } successUi {
            contactEventsSubject.onNext(ContactEvent.Updated(setOf(contactInfo)))
        }
    }

    /** Filter out users whose messages we should ignore. */
    override fun filterBlocked(users: Set<UserId>): Promise<Set<UserId>, Exception> {
        //avoid errors if the caller modifiers the set after giving it
        val usersCopy = HashSet(users)

        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.filterBlocked(usersCopy)
        }
    }

    override fun allowAll(userId: UserId): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.allowAll(userId)
        } successUi {
            withCurrentJob { doUpdateRemoteContactList() }

            contactsPersistenceManager.get(userId) mapUi {
                if (it != null)
                    contactEventsSubject.onNext(ContactEvent.Updated(setOf(it)))
            }
        }
    }

    private fun doUpdateRemoteContactList() {
        withCurrentJob { doUpdateRemoteContactList() }
    }

    override fun doRemoteSync() {
        withCurrentJob { doRemoteSync() }
    }

    override fun doLocalSync() {
        withCurrentJob { doPlatformContactSync() }
    }

    private fun onContactSyncStatusUpdate(info: ContactSyncJobInfo) {
        //if remote sync is at all enabled, we want the entire process to lock down the contact list
        if (info.remoteSync)
            contactEventsSubject.onNext(ContactEvent.Sync(info.isRunning))
    }

    /** Process the given unadded users. */
    private fun addNewContactData(users: Set<UserId>): Promise<AddContactsResult, Exception> {
        if (users.isEmpty())
            return Promise.ofSuccess(AddContactsResult(false, emptySet()))

        log.debug("Fetching missing contact info for {}", users.map { it.long })

        val request = FetchContactInfoByIdRequest(users.toList())

        return authTokenManager.bind { userCredentials ->
            contactClient.fetchContactInfoById(userCredentials, request) bindUi { response ->
                handleContactLookupResponse(users, response)
            } fail { e ->
                //the only recoverable error would be a network error; when the network is restored, this'll get called again
                log.error("Unable to fetch contact info: {}", e.message, e)
            }
        }
    }

    private fun handleContactLookupResponse(users: Set<UserId>, response: FetchContactInfoByIdResponse): Promise<AddContactsResult, Exception> {
        val foundIds = response.contacts.mapTo(HashSet()) { it.id }

        val missing = HashSet(users)
        missing.removeAll(foundIds)

        val invalidContacts = HashSet<UserId>()

        //XXX blacklist? at least temporarily or something
        if (missing.isNotEmpty())
            invalidContacts.addAll(missing)

        val contacts = response.contacts.map { it.toCore(AllowedMessageLevel.GROUP_ONLY) }

        return contactsPersistenceManager.add(contacts) mapUi { newContacts ->
            log.debug("Added new contacts: {}", newContacts.map { it.id.long })

            val added = newContacts.isNotEmpty()

            if (added) {
                val ev = ContactEvent.Added(newContacts)
                contactEventsSubject.onNext(ev)
            }

            AddContactsResult(added, invalidContacts)
        } fail { e ->
            log.error("Unable to add new contacts: {}", e.message, e)
        }
    }

    override fun addMissingContacts(users: Set<UserId>): Promise<Set<UserId>, Exception> {
        if (users.isEmpty())
            return Promise.ofSuccess(emptySet())

        //defensive copy
        val missing = HashSet(users)

        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.exists(users) bind { exists ->
                missing.removeAll(exists)
                addNewContactData(missing) mapUi {
                    if (it.added)
                        doUpdateRemoteContactList()

                    it.invalidIds
                }
            }
        }
    }

    /** Used to mark job components for execution. */
    private fun withCurrentJob(body: ContactSyncJobDescription.() -> Unit) {
        addressBookOperationManager.withCurrentSyncJob(body)
    }

    override fun shutdown() {
    }

    //FIXME return an Either<String, ApiContactInfo>
    override fun fetchRemoteContactInfo(email: String?, queryPhoneNumber: String?): Promise<FetchContactResponse, Exception> {
        return authTokenManager.bind { userCredentials ->
            val request = NewContactRequest(email, queryPhoneNumber)

            contactClient.fetchNewContactInfo(userCredentials, request)
        }
    }
}