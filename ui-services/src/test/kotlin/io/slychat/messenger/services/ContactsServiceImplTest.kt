package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.ApiContactInfo
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.FetchContactInfoByIdResponse
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import rx.observers.TestSubscriber
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactsServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        class MockContactJobRunner : ContactOperationManager {
            val runningSubject: PublishSubject<ContactSyncJobInfo> = PublishSubject.create()

            var immediate = true

            //make some makeshift verification data
            var runOperationCallCount = 0
            var withCurrentJobCallCount = 0

            override val running: Observable<ContactSyncJobInfo> = runningSubject

            override fun withCurrentSyncJob(body: ContactSyncJobDescription.() -> Unit) {
                withCurrentJobCallCount += 1
            }

            override fun shutdown() {
            }

            override fun runOperation(operation: () -> Promise<*, Exception>) {
                runOperationCallCount += 1

                if (immediate)
                    operation()
                else
                    throw UnsupportedOperationException()
            }
        }
    }

    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val contactClient: ContactAsyncClient = mock()
    val contactOperationManager = MockContactJobRunner()

    fun createService(): ContactsServiceImpl {
        return ContactsServiceImpl(
            MockAuthTokenManager(),
            contactClient,
            contactsPersistenceManager,
            contactOperationManager
        )
    }

    inline fun <reified T : ContactEvent> contactEventCollectorFor(contactsService: ContactsServiceImpl): TestSubscriber<T> {
        return contactsService.contactEvents.subclassFilterTestSubscriber()
    }

    @Test
    fun `adding a new contact should return true if the contact was added`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(true)

        assertTrue(contactsService.addContact(contactInfo).get(), "Contact not seen as added")

        assertEquals(1, contactOperationManager.runOperationCallCount, "Didn't go through ContactJobRunner")
    }

    @Test
    fun `removing a new contact should return true if the contact was removed`() {
        val contactsService = createService()

        val userId = UserId(1)

        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.remove(userId)).thenReturn(true)

        assertTrue(contactsService.removeContact(contactInfo).get(), "Contact not seen as removed")

        assertEquals(1, contactOperationManager.runOperationCallCount, "Didn't go through ContactJobRunner")
    }

    @Test
    fun `updating a contact should complete`() {
        val contactsService = createService()

        val userId = UserId(1)

        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.update(contactInfo)).thenReturn(Unit)

        //should return
        contactsService.updateContact(contactInfo).get()

        assertEquals(1, contactOperationManager.runOperationCallCount, "Didn't go through ContactJobRunner")
    }

    //TODO remove/update event tests

    @Test
    fun `adding a new contact should emit an update event if the contact is new`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

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

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(false)

        val testSubscriber = contactEventCollectorFor<ContactEvent.Added>(contactsService)

        contactsService.addContact(contactInfo)

        testSubscriber.assertNoValues()
    }

    @Test
    fun `allowMessagesFrom should filter out blocked contacts`() {
        val contactsService = createService()

        val ids = (1..3L).map { UserId(it) }
        val allowed = ids.subList(1, ids.size).toSet()
        val idSet = ids.toSet()

        whenever(contactsPersistenceManager.filterBlocked(idSet)).thenReturn(allowed)

        val gotAllowed = contactsService.allowMessagesFrom(idSet).get()

        assertEquals(allowed, gotAllowed, "Invalid allowed list")
    }

    @Test
    fun `doLocalSync should defer to ContactJobRunner`() {
        val contactsService = createService()

        contactsService.doLocalSync()

        assertEquals(1, contactOperationManager.withCurrentJobCallCount, "ContactJobRunner not called")
    }

    @Test
    fun `doRemoteSync should defer to ContactJobRunner`() {
        val contactsService = createService()

        contactsService.doRemoteSync()

        assertEquals(1, contactOperationManager.withCurrentJobCallCount, "ContactJobRunner not called")
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
    fun `addMissingContacts should go through ContactJobRunner`() {
        val contactsService = createService()

        testAddMissingContacts(
            contactsService,
            0..1L,
            { it },
            { it },
            false
        )

        assertEquals(1, contactOperationManager.runOperationCallCount, "Didn't go through ContactJobRunner")
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

        assertEquals(0, contactOperationManager.withCurrentJobCallCount, "Sync was run")
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

        assertEquals(1, contactOperationManager.withCurrentJobCallCount, "Sync was run")
    }

    fun testSyncEvent(isRunning: Boolean) {
        val contactsService = createService()

        val info = ContactSyncJobInfo(false, false, true, isRunning)

        val testSubscriber = contactEventCollectorFor<ContactEvent.Sync>(contactsService)

        contactOperationManager.runningSubject.onNext(info)

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
}
