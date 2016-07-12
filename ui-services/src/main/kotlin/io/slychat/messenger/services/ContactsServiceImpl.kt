package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.FetchContactResponse
import io.slychat.messenger.core.http.api.contacts.NewContactRequest
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class ContactsServiceImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactClient: ContactAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val contactJobRunner: ContactJobRunner
) : ContactsService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val contactEventsSubject = PublishSubject.create<ContactEvent>()

    override val contactEvents: Observable<ContactEvent> = contactEventsSubject

    init {
        contactJobRunner.running.subscribe { onContactJobStatusUpdate(it) }
    }

    private fun <V, E> wrap(deferred: Deferred<V, E>, promise: Promise<V, E>): Promise<V, E> {
        return promise success { deferred.resolve(it) } fail { deferred.reject(it) }
    }

    override fun addContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        val d = deferred<Boolean, Exception>()

        contactJobRunner.runOperation {
            wrap(d, contactsPersistenceManager.add(contactInfo)) successUi { wasAdded ->
                if (wasAdded) {
                    withCurrentJob { doUpdateRemoteContactList() }
                    contactEventsSubject.onNext(ContactEvent.Added(setOf(contactInfo)))
                }
            }
        }

        return d.promise
    }

    /** Remove the given contact from the contact list. */
    override fun removeContact(contactInfo: ContactInfo): Promise<Boolean, Exception> {
        val d = deferred<Boolean, Exception>()

        contactJobRunner.runOperation {
            wrap(d, contactsPersistenceManager.remove(contactInfo.id)) successUi { wasRemoved ->
                if (wasRemoved) {
                    withCurrentJob { doUpdateRemoteContactList() }
                    contactEventsSubject.onNext(ContactEvent.Removed(setOf(contactInfo)))
                }
            }
        }

        return d.promise
    }

    override fun updateContact(contactInfo: ContactInfo): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()

        contactJobRunner.runOperation {
            wrap(d, contactsPersistenceManager.update(contactInfo)) successUi {
                contactEventsSubject.onNext(ContactEvent.Updated(setOf(contactInfo)))
            }
        }

        return d.promise
    }

    /** Filter out users whose messages we should ignore. */
    //in the future, this will also check for blocked/deleted users
    override fun allowMessagesFrom(users: Set<UserId>): Promise<Set<UserId>, Exception> {
        val d = deferred<Set<UserId>, Exception>()

        //avoid errors if the caller modifiers the set after giving it
        val usersCopy = HashSet(users)

        contactJobRunner.runOperation {
            wrap(d, contactsPersistenceManager.filterBlocked(usersCopy))
        }

        return d.promise
    }

    override fun doRemoteSync() {
        withCurrentJob { doRemoteSync() }
    }

    override fun doLocalSync() {
        withCurrentJob { doLocalSync() }
    }

    private fun onContactJobStatusUpdate(info: ContactJobInfo) {
        //if remote sync is at all enabled, we want the entire process to lock down the contact list
        if (info.remoteSync)
            contactEventsSubject.onNext(ContactEvent.Sync(info.isRunning))
    }

    //XXX doesn't work because this needs to be done as a job, else it can clash with offline/etc stuff
    //we can't rely on events, since we could be waiting on a ui contact add
    //we can't return the current job promise since we want the promise corresponding to the next job
    override fun addMissingContacts(users: Set<UserId>): Promise<Unit, Exception> {
        //defensive copy
        val missing = HashSet(users)

        throw NotImplementedError()
        /**
        return contactsPersistenceManager.exists(users) bind { exists ->
            missing.removeAll(exists)

            addPendingContacts(missing)
        } successUi {
            doRemoteSync()
        }
        **/
    }

    /** Used to mark job components for execution. */
    private fun withCurrentJob(body: ContactJobDescription.() -> Unit) {
        contactJobRunner.withCurrentJob(body)
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