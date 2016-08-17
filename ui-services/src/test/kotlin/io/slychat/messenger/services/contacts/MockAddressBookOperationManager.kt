package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockAddressBookOperationManager : AddressBookOperationManager {
    val runningSubject: PublishSubject<AddressBookSyncJobInfo> = PublishSubject.create()

    private var currentSyncJobDescription: AddressBookSyncJobDescription = AddressBookSyncJobDescription()

    var immediate = true

    //make some makeshift verification data
    var runOperationCallCount = 0
    var withCurrentJobCallCount = 0

    override val running: Observable<AddressBookSyncJobInfo> = runningSubject

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

    fun assertRemoteUpdateTriggered() {
        assertTrue(currentSyncJobDescription.push, "Remote contact list update sync not triggered")
    }

    fun assertRemoteSyncTriggered() {
        assertTrue(currentSyncJobDescription.pull, "Remote sync not triggered")
    }

    fun assertPlatformContactSyncTriggered() {
        assertTrue(currentSyncJobDescription.findPlatformContacts, "Platform contact sync not triggered")
    }

    fun assertRemoteUpdateNotTriggered() {
        assertFalse(currentSyncJobDescription.push, "Remote contact list update sync should not be triggered")
    }

    fun assertRemoteSyncNotTriggered() {
        assertFalse(currentSyncJobDescription.pull, "Remote sync should not triggered")
    }

    fun assertPlatformContactSyncNotTriggered() {
        assertFalse(currentSyncJobDescription.findPlatformContacts, "Platform contact sync should not triggered")
    }
}