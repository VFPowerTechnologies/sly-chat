package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.http.api.contacts.ApiContactInfo
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.FetchContactInfoByIdResponse
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import nl.komponents.kovenant.Promise
import org.assertj.core.api.AbstractIterableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.ClassRule
import org.junit.Test
import org.mockito.stubbing.OngoingStubbing
import rx.Observable
import rx.observers.TestSubscriber
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun <T> OngoingStubbing<Promise<T, Exception>>.thenReturn(v: T) {
    this.thenReturn(Promise.ofSuccess(v))
}

fun <T> OngoingStubbing<Promise<T, Exception>>.thenReturn(e: Exception) {
    this.thenReturn(Promise.ofFail(e))
}

fun <T> cond(description: String, predicate: (T) -> Boolean): Condition<T> = object : Condition<T>(description) {
    override fun matches(value: T): Boolean = predicate(value)
}

fun <T> AbstractIterableAssert<*, *, T, *>.have(description: String, predicate: (T) -> Boolean): AbstractIterableAssert<*, *, T, *> {
    this.have(cond(description, predicate))
    return this
}

@Suppress("UNCHECKED_CAST")
class ContactsServiceTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val testPassword = "test"
        val testKeyVault = generateNewKeyVault(testPassword)

        val testUserAddress = SlyAddress(UserId(1), 1)
        val userLoginData = UserData(testUserAddress, testKeyVault)
    }

    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val contactClient: ContactAsyncClient = mock()

    fun createService(networkIsAvailable: Boolean = true): ContactsService {
        return ContactsService(
            MockAuthTokenManager(),
            Observable.just(networkIsAvailable),
            contactClient,
            mock(),
            contactsPersistenceManager,
            userLoginData,
            mock(),
            mock()
        )

    }

    inline fun <reified T : Any> eventCollectorFor(contactsService: ContactsService): TestSubscriber<T> {
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
    fun `it should emit an InvalidContacts event for contacts not returned by the server`() {
        val contactsService = createService()

        val unadded = (1..3L).map { UserId(it) }
        val missing = setOf(unadded.first())
        val present = unadded.subList(1, unadded.size).map { ApiContactInfo(it, "email", "name", "", "pubkey") }

        whenever(contactsPersistenceManager.getUnadded()).thenReturn(unadded.toSet())

        val response = FetchContactInfoByIdResponse(present)
        whenever(contactClient.fetchContactInfoById(any(), any())).thenReturn(response)

        val testSubscriber = eventCollectorFor<ContactEvent.InvalidContacts>(contactsService)

        contactsService.doProcessUnaddedContacts()

        assertEventEmitted(testSubscriber) { event ->
            assertEquals(missing, event.contacts, "InvalidContacts contains the wrong users")
        }
    }

    @Test
    fun `when processing unadded, it should add and then emit the returned contacts via an Added event for contacts returned by the server`() {
        val contactsService = createService()

        val unadded = (1..4L).map { UserId(it) }
        val present = unadded.subList(1, unadded.size)
        val newContactIds = present.subList(0, 2).toSet()
        val presentRemote = present.map { ApiContactInfo(it, "email", "name", "", "pubkey") }

        val response = FetchContactInfoByIdResponse(presentRemote)

        val testSubscriber = eventCollectorFor<ContactEvent.Added>(contactsService)

        whenever(contactsPersistenceManager.getUnadded()).thenReturn(unadded.toSet())

        whenever(contactClient.fetchContactInfoById(any(), any())).thenReturn(response)

        whenever(contactsPersistenceManager.add(any<Collection<ContactInfo>>())).thenAnswer {
            val a = it.arguments[0] as Collection<ContactInfo>
            val r = a.filter { newContactIds.contains(it.id) }.toSet()
            Promise.ofSuccess<Set<ContactInfo>, Exception>(r)
        }

        contactsService.doProcessUnaddedContacts()

        assertEventEmitted(testSubscriber) { event ->
            assertThat(event.contacts.map { it.id }).containsOnlyElementsOf(newContactIds)

            event.contacts.forEach {
                assertTrue(it.isPending, "isPending should be true for $it")
                assertEquals(AllowedMessageLevel.GROUP_ONLY, it.allowedMessageLevel, "allowedMessageLevel should be GROUP_ONLY for $it")
            }
        }
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

    //TODO test remote contact list updates

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

    //TODO test local contact sync message level/pending
    //TODO test local contact remote lookup
    //TODO test remote sync message level/pending
}
