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
import org.assertj.core.api.AbstractIterableAssert
import org.assertj.core.api.Condition
import org.junit.ClassRule
import org.junit.Test
import rx.observers.TestSubscriber
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun <T> cond(description: String, predicate: (T) -> Boolean): Condition<T> = object : Condition<T>(description) {
    override fun matches(value: T): Boolean = predicate(value)
}

fun <T> AbstractIterableAssert<*, *, T, *>.have(description: String, predicate: (T) -> Boolean): AbstractIterableAssert<*, *, T, *> {
    this.have(cond(description, predicate))
    return this
}

@Suppress("UNCHECKED_CAST")
class ContactsServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val contactClient: ContactAsyncClient = mock()
    val contactJobRunner: ContactJobRunner = mock()

    val contactJobRunning: PublishSubject<ContactJobInfo> = PublishSubject.create()

    fun createService(): ContactsServiceImpl {
        whenever(contactJobRunner.running).thenReturn(contactJobRunning)

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
}
