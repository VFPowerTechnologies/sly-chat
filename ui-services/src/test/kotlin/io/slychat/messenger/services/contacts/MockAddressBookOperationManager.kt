package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject

class MockAddressBookOperationManager : AddressBookOperationManager {
    val runningSubject: PublishSubject<AddressBookSyncJobInfo> = PublishSubject.create()

    var immediate = true

    //make some makeshift verification data
    var runOperationCallCount = 0
    var withCurrentJobCallCount = 0

    override val running: Observable<AddressBookSyncJobInfo> = runningSubject

    override fun withCurrentSyncJob(body: AddressBookSyncJobDescription.() -> Unit) {
        withCurrentJobCallCount += 1
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
}