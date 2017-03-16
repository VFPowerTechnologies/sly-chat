package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import io.slychat.messenger.services.contacts.AddressBookSyncEvent
import io.slychat.messenger.services.contacts.AddressBookSyncJobInfo
import io.slychat.messenger.services.contacts.AddressBookSyncResult
import io.slychat.messenger.services.contacts.PullResults
import io.slychat.messenger.services.messaging.MessengerService
import org.junit.Test
import rx.subjects.PublishSubject

class AddressBookSyncWatcherImplTest {
    private val syncEvents: PublishSubject<AddressBookSyncEvent> = PublishSubject.create()
    private val messengerService: MessengerService = mock()
    private val watcher = AddressBookSyncWatcherImpl(syncEvents, messengerService)

    @Test
    fun `it should call broadcastSync when a sync job completes with a non-zero updateCount`() {
        val info = AddressBookSyncJobInfo(true, true, true)
        val result = AddressBookSyncResult(true, 1, PullResults(false))

        syncEvents.onNext(AddressBookSyncEvent.End(info, result))

        verify(messengerService).broadcastAddressBookSync()
    }

    @Test
    fun `it should not call broadcastSync when a sync job completes with a zero updateCount`() {
        val info = AddressBookSyncJobInfo(true, true, true)
        val result = AddressBookSyncResult(true, 0, PullResults(false))

        syncEvents.onNext(AddressBookSyncEvent.End(info, result))

        verify(messengerService, never()).broadcastAddressBookSync()
    }
}