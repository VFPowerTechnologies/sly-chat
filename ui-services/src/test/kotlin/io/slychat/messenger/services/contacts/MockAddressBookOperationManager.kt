package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockAddressBookOperationManager : AddressBookOperationManager {
    val syncEventsSubject: PublishSubject<AddressBookSyncEvent> = PublishSubject.create()

    private var currentSyncJobDescription: AddressBookSyncJobDescription = AddressBookSyncJobDescription()

    var immediate = true

    //make some makeshift verification data
    var runOperationCallCount = 0
    var withCurrentJobCallCount = 0

    override val syncEvents: Observable<AddressBookSyncEvent> = syncEventsSubject

    override fun withCurrentSyncJob(body: AddressBookSyncJobDescription.() -> Unit) {
        withCurrentJobCallCount += 1
        currentSyncJobDescription.body()
    }

    override fun withCurrentSyncJobNoScheduler(body: AddressBookSyncJobDescription.() -> Unit) {
        withCurrentSyncJob(body)
    }

    override fun shutdown() {
    }

    override fun <T> runOperation(operation: () -> Promise<T, Exception>): Promise<T, Exception> {
        runOperationCallCount += 1

        return if (immediate) {
            operation()
        }
        else
            throw UnsupportedOperationException()
    }

    fun assertPushTriggered() {
        assertTrue(currentSyncJobDescription.push, "Push not triggered")
    }

    fun assertPullTriggered() {
        assertTrue(currentSyncJobDescription.pull, "Pull not triggered")
    }

    fun assertFindPlatformContactsTriggered() {
        assertTrue(currentSyncJobDescription.findPlatformContacts, "Find platform contacts not triggered")
    }

    fun assertPushNotTriggered() {
        assertFalse(currentSyncJobDescription.push, "Push should not be triggered")
    }

    fun assertPullNotTriggered() {
        assertFalse(currentSyncJobDescription.pull, "PUll should not be triggered")
    }

    fun assertFindPlatformContactsNotTriggered() {
        assertFalse(currentSyncJobDescription.findPlatformContacts, "Find platform contacts should not be triggered")
    }
}