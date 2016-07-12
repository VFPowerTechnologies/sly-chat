package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import rx.observers.TestSubscriber
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactsServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        class MockContactJobRunner : ContactJobRunner {
            val subject: PublishSubject<ContactJobInfo> = PublishSubject.create()

            var immediate = true

            //make some makeshift verification data
            var runOperationCallCount = 0
            var withCurrentJobCallCount = 0

            override val running: Observable<ContactJobInfo> = subject

            override fun withCurrentJob(body: ContactJobDescription.() -> Unit) {
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
    val contactJobRunner = MockContactJobRunner()

    fun createService(): ContactsServiceImpl {
        return ContactsServiceImpl(
            MockAuthTokenManager(),
            contactClient,
            contactsPersistenceManager,
            contactJobRunner
        )
    }

    inline fun <reified T : Any> eventCollectorFor(contactsService: ContactsServiceImpl): TestSubscriber<T> {
        val testSubscriber = TestSubscriber<T>()
        contactsService.contactEvents.filter { it is T }.cast(T::class.java).subscribe(testSubscriber)
        return testSubscriber
    }

    fun <T : ContactEvent> assertEventEmitted(testSubscriber: TestSubscriber<T>, asserter: (T) -> Unit) {
        val events = testSubscriber.onNextEvents

        assertTrue(events.isNotEmpty(), "No event emitted")

        val event = events.first()

        asserter(event)
    }

    @Test
    fun `adding a new contact should return true if the contact was added`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(true)

        assertTrue(contactsService.addContact(contactInfo).get(), "Contact not seen as added")

        assertEquals(1, contactJobRunner.runOperationCallCount, "Didn't go through ContactJobRunner")
    }

    @Test
    fun `removing a new contact should return true if the contact was removed`() {
        val contactsService = createService()

        val userId = UserId(1)

        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.remove(userId)).thenReturn(true)

        assertTrue(contactsService.removeContact(contactInfo).get(), "Contact not seen as removed")

        assertEquals(1, contactJobRunner.runOperationCallCount, "Didn't go through ContactJobRunner")
    }

    @Test
    fun `updating a contact should complete`() {
        val contactsService = createService()

        val userId = UserId(1)

        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.update(contactInfo)).thenReturn(Unit)

        //should return
        contactsService.updateContact(contactInfo).get()

        assertEquals(1, contactJobRunner.runOperationCallCount, "Didn't go through ContactJobRunner")
    }

    //TODO remove/update event tests

    @Test
    fun `adding a new contact should emit an update event if the contact is new`() {
        val contactsService = createService()

        val contactInfo = ContactInfo(UserId(1), "email", "name", AllowedMessageLevel.ALL, false, "", "pubkey")

        whenever(contactsPersistenceManager.add(contactInfo)).thenReturn(true)

        val testSubscriber = eventCollectorFor<ContactEvent.Added>(contactsService)

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

        val testSubscriber = eventCollectorFor<ContactEvent.Added>(contactsService)

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

        assertEquals(1, contactJobRunner.withCurrentJobCallCount, "ContactJobRunner not called")
    }

    @Test
    fun `doRemoteSync should defer to ContactJobRunner`() {
        val contactsService = createService()

        contactsService.doRemoteSync()

        assertEquals(1, contactJobRunner.withCurrentJobCallCount, "ContactJobRunner not called")
    }
}
