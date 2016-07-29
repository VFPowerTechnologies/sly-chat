package io.slychat.messenger.services.contacts

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.ApiContactInfo
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.FetchContactInfoByIdResponse
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.randomContactInfo
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.services.assertEventEmitted
import io.slychat.messenger.services.assertNoEventsEmitted
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.services.subclassFilterTestSubscriber
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.observers.TestSubscriber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactsServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val contactClient: ContactAsyncClient = mock()
    val addressBookOperationManager = MockAddressBookOperationManager()

    fun createService(): ContactsServiceImpl {
        return ContactsServiceImpl(
            MockAuthTokenManager(),
            contactClient,
            contactsPersistenceManager,
            addressBookOperationManager
        )
    }

    fun assertOperationManagerUsed() {
        assertTrue(addressBookOperationManager.runOperationCallCount == 1, "Didn't go through AddressBookOperationManager")
    }

    fun assertOperationManagerCalledForSync() {
        assertEquals(1, addressBookOperationManager.withCurrentJobCallCount, "AddressBookOperationManager not called for sync")

    }

    inline fun <reified T : ContactEvent> contactEventCollectorFor(contactsService: ContactsServiceImpl): TestSubscriber<T> {
        return contactsService.contactEvents.subclassFilterTestSubscriber()
    }

    @Test
    fun `adding a new contact should return true if the contact was added`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(true)

        assertTrue(contactsService.addContact(contactInfo).get(), "Contact not seen as added")

        assertOperationManagerUsed()
    }

    @Test
    fun `removing a new contact should return true if the contact was removed`() {
        val contactsService = createService()

        val userId = UserId(1)

        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, "", "pubkey")

        whenever(contactsPersistenceManager.remove(userId)).thenReturn(true)

        assertTrue(contactsService.removeContact(contactInfo).get(), "Contact not seen as removed")

        assertOperationManagerUsed()
    }

    @Test
    fun `updating a contact should complete`() {
        val contactsService = createService()

        val userId = UserId(1)

        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, "", "pubkey")

        whenever(contactsPersistenceManager.update(contactInfo)).thenReturn(Unit)

        //should return
        contactsService.updateContact(contactInfo).get()

        assertOperationManagerUsed()
    }

    @Test
    fun `adding a new contact should emit an update event if the contact is new`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(true)

        val testSubscriber = contactEventCollectorFor<ContactEvent.Added>(contactsService)

        contactsService.addContact(contactInfo)

        assertEventEmitted(testSubscriber) { event ->
            val contacts = event.contacts
            assertEquals(1, contacts.size, "Invalid number of contacts")

            assertEquals(contactInfo, contacts.first(), "Added contains the wrong users")
        }
    }

    @Test
    fun `adding a new contact should not emit an update event if the contact is already added`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(false)

        val testSubscriber = contactEventCollectorFor<ContactEvent.Added>(contactsService)

        contactsService.addContact(contactInfo)

        testSubscriber.assertNoValues()
    }

    @Test
    fun `filterBlocked should filter out blocked contacts`() {
        val contactsService = createService()

        val ids = (1..3L).map { UserId(it) }
        val allowed = ids.subList(1, ids.size).toSet()
        val idSet = ids.toSet()

        whenever(contactsPersistenceManager.filterBlocked(idSet)).thenReturn(allowed)

        val gotAllowed = contactsService.filterBlocked(idSet).get()

        assertEquals(allowed, gotAllowed, "Invalid allowed list")
    }

    @Test
    fun `doLocalSync should defer to AddressBookOperationManager`() {
        val contactsService = createService()

        contactsService.doLocalSync()

        assertOperationManagerCalledForSync()
    }

    @Test
    fun `doRemoteSync should defer to AddressBookOperationManager`() {
        val contactsService = createService()

        contactsService.doRemoteSync()

        assertOperationManagerCalledForSync()
    }

    fun testAddMissingContacts(
        contactsService: ContactsServiceImpl,
        inputs: LongRange,
        localExists: (Set<UserId>) -> Set<UserId>,
        remoteExists: (Set<UserId>) -> Set<UserId>,
        added: Boolean
    ): Set<UserId> {
        val ids = inputs.map { UserId(it) }.toSet()

        val presentContacts = localExists(ids)

        whenever(contactsPersistenceManager.exists(any<Set<UserId>>())).thenReturn(presentContacts)

        whenever(contactsPersistenceManager.add(any<Collection<ContactInfo>>())).thenAnswer {
            val v = if (added) {
                @Suppress("UNCHECKED_CAST")
                val a = it.arguments[0] as Collection<ContactInfo>
                a.toSet()
            }
            else
                emptySet()

            Promise.ofSuccess<Set<ContactInfo>, Exception>(v)
        }

        val apiContacts = remoteExists(ids).map { ApiContactInfo(it, "$it", "$it", "$it", "pubkey") }

        val response = FetchContactInfoByIdResponse(apiContacts)

        whenever(contactClient.fetchContactInfoById(any(), any())).thenReturn(response)

        return contactsService.addMissingContacts(ids).get()
    }

    @Test
    fun `addMissingContacts should do nothing when receiving an empty set`() {
        val contactsService = createService()

        contactsService.addMissingContacts(emptySet()).get()

        verify(contactsPersistenceManager, never()).add(any<Collection<ContactInfo>>())
    }

    @Test
    fun `addMissingContacts should fire an Added event when a contact is added`() {
        val contactsService = createService()

        val testSubscriber = contactEventCollectorFor<ContactEvent.Added>(contactsService)

        val invalidIds = testAddMissingContacts(
            contactsService,
            0..1L,
            { emptySet() },
            { it },
            true
        )

        assertThat(invalidIds)
            .`as`("Invalid ids")
            .isEmpty()

        assertEventEmitted(testSubscriber) { event ->
            val contacts = event.contacts
            assertEquals(2, contacts.size, "Invalid number of contacts")
        }
    }

    @Test
    fun `addMissingContacts should not fire an Added event when no contacts are added`() {
        val contactsService = createService()

        val testSubscriber = contactEventCollectorFor<ContactEvent.Added>(contactsService)

        val invalidIds = testAddMissingContacts(
            contactsService,
            0..1L,
            { emptySet() },
            { it },
            false
        )

        assertThat(invalidIds)
            .`as`("Invalid ids")
            .isEmpty()

        assertNoEventsEmitted(testSubscriber)
    }

    @Test
    fun `addMissingContacts should return invalid user ids`() {
        val contactsService = createService()

        val invalidIds = testAddMissingContacts(
            contactsService,
            0..1L,
            { emptySet() },
            { setOf(it.first()) },
            true
        )

        assertThat(invalidIds)
            .containsOnly(UserId(1))
            .`as`("Invalid ids")
    }

    @Test
    fun `addMissingContacts should do nothing if all contacts exist`() {
        val contactsService = createService()

        val testSubscriber = contactEventCollectorFor<ContactEvent.Added>(contactsService)

        val invalidIds = testAddMissingContacts(
            contactsService,
            0..1L,
            { it },
            { it },
            false
        )

        assertThat(invalidIds)
            .`as`("Invalid ids")
            .isEmpty()

        assertNoEventsEmitted(testSubscriber)
    }

    @Test
    fun `addMissingContacts should go through AddressBookOperationManager`() {
        val contactsService = createService()

        testAddMissingContacts(
            contactsService,
            0..1L,
            { it },
            { it },
            false
        )

        assertOperationManagerUsed()
    }

    @Test
    fun `addMissingContacts should not cause a sync if no contacts are added`() {
        val contactsService = createService()

        testAddMissingContacts(
            contactsService,
            0..1L,
            { it },
            { it },
            false
        )

        assertEquals(0, addressBookOperationManager.withCurrentJobCallCount, "Sync was run")
    }

    @Test
    fun `addMissingContacts should cause a sync if contacts are added`() {
        val contactsService = createService()

        testAddMissingContacts(
            contactsService,
            0..1L,
            { emptySet() },
            { it },
            true
        )

        assertOperationManagerCalledForSync()
    }

    fun testSyncEvent(isRunning: Boolean) {
        val contactsService = createService()

        val info = ContactSyncJobInfo(false, false, true, isRunning)

        val testSubscriber = contactEventCollectorFor<ContactEvent.Sync>(contactsService)

        addressBookOperationManager.runningSubject.onNext(info)

        assertEventEmitted(testSubscriber) { ev ->
            if (isRunning)
                assertTrue(ev.isRunning, "Not a running event")
            else
                assertFalse(ev.isRunning, "Not a stopped event")
        }

    }

    @Test
    fun `it should fire a sync start event when a the job runner starts running a remote sync`() {
        testSyncEvent(true)
    }

    @Test
    fun `it should fire a sync stop event when a the job runner starts running a sync`() {
        testSyncEvent(false)
    }

    @Test
    fun `allowAll should update the message level for the given user`() {
        val userId = randomUserId()

        whenever(contactsPersistenceManager.get(userId)).thenReturn(randomContactInfo().copy(id = userId))
        whenever(contactsPersistenceManager.allowAll(userId)).thenReturn(Unit)

        val contactsService = createService()

        contactsService.allowAll(userId).get()

        verify(contactsPersistenceManager).allowAll(userId)
    }

    @Test
    fun `allowAll should trigger a remote update`() {
        val userId = randomUserId()
        whenever(contactsPersistenceManager.allowAll(any())).thenReturn(Unit)
        whenever(contactsPersistenceManager.get(userId)).thenReturn(randomContactInfo().copy(id = userId))

        val contactsService = createService()

        contactsService.allowAll(userId).get()

        assertTrue(addressBookOperationManager.withCurrentJobCallCount == 1, "Remote sync not triggered")
    }

    @Test
    fun `allowAll should fire a contact updated event`() {
        val userId = randomUserId()
        whenever(contactsPersistenceManager.allowAll(any())).thenReturn(Unit)
        whenever(contactsPersistenceManager.get(userId)).thenReturn(randomContactInfo().copy(id = userId))

        val contactsService = createService()

        val testSubscriber = contactEventCollectorFor<ContactEvent.Updated>(contactsService)

        contactsService.allowAll(userId).get()

        assertEventEmitted(testSubscriber) { ev ->
            assertEquals(1, ev.contacts.size, "Invalid number of updated contacts")
            val c = ev.contacts.first()

            assertEquals(AllowedMessageLevel.ALL, c.allowedMessageLevel, "Invalid message level")
        }
    }
}
