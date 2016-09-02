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

    override val contactEvents: Observable<ContactEvent>
        get() = contactEventsSubject

    init {
        addressBookOperationManager.syncEvents.subscribe { onAddressBookSyncStatusUpdate(it) }
    }

    override fun addByInfo(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Adding new contact: {}", contactInfo.id)
            contactsPersistenceManager.add(contactInfo)
        } successUi { wasAdded ->
            if (wasAdded) {
                withCurrentJob { doPush() }
                contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
            }
        }
    }

    private fun addNewUserRemotely(userId: UserId): Promise<Boolean, Exception> {
        return authTokenManager.bind { userCredentials ->
            contactClient.findById(userCredentials, userId)
        } bind { response ->
            val contactInfo = response.contactInfo?.toCore(AllowedMessageLevel.ALL)
            if (contactInfo == null)
                Promise.ofSuccess(false)
            else {
                contactsPersistenceManager.add(contactInfo) successUi {
                    if (it)
                        contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
                }
            }
        }
    }

    private fun upgradeUserMessageLevel(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        if (contactInfo.allowedMessageLevel == AllowedMessageLevel.ALL)
            return Promise.ofSuccess(false)

        return contactsPersistenceManager.allowAll(contactInfo.id) mapUi {
            val contactUpdate = ContactUpdate(
                contactInfo,
                contactInfo.copy(allowedMessageLevel = AllowedMessageLevel.ALL)
            )

            contactEventsSubject.onNext(ContactEvent.Updated(setOf(contactUpdate)))
            true
        }
    }

    override fun addById(userId: UserId): Promise<Boolean, Exception> {
        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.get(userId) bind {
                //don't need to worry about blacklisted users, as their messages never even get decrypted
                if (it != null)
                    upgradeUserMessageLevel(it)
                else
                    addNewUserRemotely(userId)
            }
        }
    }

    override fun addSelf(selfInfo: ContactInfo): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Adding self info: {}", selfInfo)
            contactsPersistenceManager.addSelf(selfInfo)
        }
    }

    /** Remove the given contact from the contact list. */
    override fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return addressBookOperationManager.runOperation {
            val id = contactInfo.id
            log.debug("Removing contact: {}", id)
            contactsPersistenceManager.remove(id)
        } successUi { wasRemoved ->
            if (wasRemoved) {
                withCurrentJob { doPush() }
                contactEventsSubject.onNext(ContactEvent.Removed(setOf(contactInfo)))
            }
        }
    }

    override fun updateContact(contactInfo: ContactInfo): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            contactsPersistenceManager.get(contactInfo.id) bind { oldInfo ->
                if (oldInfo == null)
                    throw IllegalStateException("Unable to find user: ${contactInfo.id}")

                log.debug("Updating contact: {}", contactInfo.id)

                contactsPersistenceManager.update(contactInfo) successUi {
                    val contactUpdate = ContactUpdate(oldInfo, contactInfo)
                    contactEventsSubject.onNext(ContactEvent.Updated(setOf(contactUpdate)))
                }
            }
        }
    }

    /** Filter out users whose messages we should ignore. */
    override fun filterBlocked(users: Set<UserId>): Promise<Set<UserId>, Exception> {
        //avoid errors if the caller modifiers the set after giving it
        val usersCopy = HashSet(users)

        return addressBookOperationManager.runOperation {
            log.debug("Filtering out blocked users from: {}", users)
            contactsPersistenceManager.filterBlocked(usersCopy)
        }
    }

    override fun allowAll(userId: UserId): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Setting allowedMessageLevel=ALL for: {}", userId)

            contactsPersistenceManager.get(userId) bind { oldInfo ->
                if (oldInfo == null)
                    throw IllegalStateException("Unable to find user: $userId")

                contactsPersistenceManager.allowAll(userId) successUi {
                    withCurrentJob { doPush() }

                    val contactUpdate = ContactUpdate(
                        oldInfo,
                        oldInfo.copy(allowedMessageLevel = AllowedMessageLevel.ALL)
                    )

                    contactEventsSubject.onNext(ContactEvent.Updated(setOf(contactUpdate)))
                }
            }
        }
    }

    private fun doAddressBookPush() {
        withCurrentJob { doPush() }
    }

    override fun doAddressBookPull() {
        withCurrentJob { doPull() }
    }

    override fun doFindPlatformContacts() {
        withCurrentJob { doFindPlatformContacts() }
    }

    private fun onAddressBookSyncStatusUpdate(event: AddressBookSyncEvent) {
        //if remote sync is at all enabled, we want the entire process to lock down the contact list
        if (event.info.pull) {
            val isRunning = when (event) {
                is AddressBookSyncEvent.Begin -> true
                is AddressBookSyncEvent.End -> false
            }

            contactEventsSubject.onNext(ContactEvent.Sync(isRunning))
        }

        if (event is AddressBookSyncEvent.End) {
            if (event.result.addedLocalContacts.isNotEmpty())
                contactEventsSubject.onNext(ContactEvent.Added(event.result.addedLocalContacts))
        }
    }

    /** Process the given unadded users. */
    private fun addNewContactData(users: Set<UserId>): Promise<AddContactsResult, Exception> {
        if (users.isEmpty())
            return Promise.ofSuccess(AddContactsResult(false, emptySet()))

        log.debug("Fetching missing contact info for {}", users.map { it.long })

        val request = FindAllByIdRequest(users.toList())

        return authTokenManager.bind { userCredentials ->
            contactClient.findAllById(userCredentials, request) bindUi { response ->
                handleContactLookupResponse(users, response)
            } fail { e ->
                //the only recoverable error would be a network error; when the network is restored, this'll get called again
                log.error("Unable to fetch contact info: {}", e.message, e)
            }
        }
    }

    private fun handleContactLookupResponse(users: Set<UserId>, response: FindAllByIdResponse): Promise<AddContactsResult, Exception> {
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
            log.debug("Adding missing contacts: {}", users)
            contactsPersistenceManager.exists(users) bind { exists ->
                missing.removeAll(exists)
                addNewContactData(missing) mapUi {
                    if (it.added)
                        doAddressBookPush()

                    it.invalidIds
                }
            }
        }
    }

    /** Used to mark job components for execution. */
    private fun withCurrentJob(body: AddressBookSyncJobDescription.() -> Unit) {
        addressBookOperationManager.withCurrentSyncJob(body)
    }

    override fun shutdown() {
    }

    //FIXME return an Either<String, ApiContactInfo>
    override fun fetchRemoteContactInfo(email: String?, queryPhoneNumber: String?): Promise<FindContactResponse, Exception> {
        return authTokenManager.bind { userCredentials ->
            val request = FindContactRequest(email, queryPhoneNumber)

            contactClient.find(userCredentials, request)
        }
    }
}