package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.integration.utils.DevClient
import io.slychat.messenger.core.integration.utils.SiteUserManagement
import io.slychat.messenger.core.integration.utils.getUserCredentials
import io.slychat.messenger.core.integration.utils.serverBaseUrl
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.RemoteAddressBookEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebApiAddressBookTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    @Before
    fun before() {
        devClient.clear()
    }

    private fun assertAddressBookEquals(expected: List<RemoteAddressBookEntry>, actual: List<RemoteAddressBookEntry>, message: String? = null) {
        assertThat(actual).apply {
            `as`("Address book should match")
            containsOnlyElementsOf(expected)
        }
    }

    @Test
    fun `updates that have no effects should be reported by the server`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com")
        val userB = userManagement.injectNamedSiteUser("b@a.com")

        val contactList = encryptRemoteAddressBookEntries(userA.keyVault, listOf(userB).map { AddressBookUpdate.Contact(it.user.id, AllowedMessageLevel.ALL) })

        devClient.addAddressBookEntries(userA.user.email, contactList)

        val client = AddressBookClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.email)

        val updates = listOf(
            AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.ALL)
        )

        val updated = encryptRemoteAddressBookEntries(userA.keyVault, updates)
        val localHash = hashFromRemoteAddressBookEntries(updated)

        val request = UpdateAddressBookRequest(localHash, updated)

        val response = client.update(userA.getUserCredentials(authToken), request)

        assertFalse(response.updated, "Server reports that updates occured")
    }

    @Test
    fun `Updating the address book should create new contact entries`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com")
        val userB = userManagement.injectNamedSiteUser("b@a.com")

        val authToken = devClient.createAuthToken(userA.user.email)
        val aContacts = encryptRemoteAddressBookEntries(userA.keyVault, listOf(AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.ALL)))
        val localHash = hashFromRemoteAddressBookEntries(aContacts)

        val client = AddressBookClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())
        val userCredentials = userA.getUserCredentials(authToken)
        client.update(userCredentials, UpdateAddressBookRequest(localHash, aContacts))

        val contacts = devClient.getAddressBook(userA.user.email)

        assertAddressBookEquals(aContacts, contacts)
    }

    @Test
    fun `Fetching the address book should fetch only entries for that user`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com")
        val userB = userManagement.injectNamedSiteUser("b@a.com")
        val userC = userManagement.injectNamedSiteUser("c@a.com")

        val aContacts = encryptRemoteAddressBookEntries(userA.keyVault, listOf(AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.ALL)))
        val bContacts = encryptRemoteAddressBookEntries(userB.keyVault, listOf(AddressBookUpdate.Contact(userC.user.id, AllowedMessageLevel.ALL)))

        devClient.addAddressBookEntries(userA.user.email, aContacts)
        devClient.addAddressBookEntries(userB.user.email, bContacts)

        val client = AddressBookClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.email)
        val response = client.get(userA.getUserCredentials(authToken), GetAddressBookRequest(io.slychat.messenger.core.integration.utils.emptyMd5))

        assertAddressBookEquals(aContacts, response.entries)
    }

    @Test
    fun `Fetching the address book when hash has not changed should return an empty list`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com")
        val userB = userManagement.injectNamedSiteUser("b@a.com")
        val username = userA.user.email

        val contacts = encryptRemoteAddressBookEntries(userA.keyVault, listOf(AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.ALL)))

        devClient.addAddressBookEntries(username, contacts)

        val client = AddressBookClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val currentHash = devClient.getAddressBookHash(username)

        val authToken = devClient.createAuthToken(username)
        val response = client.get(userA.getUserCredentials(authToken), GetAddressBookRequest(currentHash))

        assertThat(response.entries).apply {
            `as`("Should not return any entries")
            isEmpty()
        }
    }

    @Test
    fun `updating the address book should add return the updated hash`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com")
        val userB = userManagement.injectNamedSiteUser("b@a.com")
        val userC = userManagement.injectNamedSiteUser("c@a.com")

        val client = AddressBookClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.email)

        val updates = listOf(
            AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.GROUP_ONLY),
            AddressBookUpdate.Contact(userC.user.id, AllowedMessageLevel.BLOCKED)
        )

        val updated = encryptRemoteAddressBookEntries(userA.keyVault, updates)
        val localHash = hashFromRemoteAddressBookEntries(updated)

        val request = UpdateAddressBookRequest(localHash, updated)

        val response = client.update(userA.getUserCredentials(authToken), request)

        assertEquals(localHash, response.hash, "Server hash doesn't match local hash")
    }

    @Test
    fun `Updating the address book should update the given contacts`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com")
        val userB = userManagement.injectNamedSiteUser("b@a.com")
        val userC = userManagement.injectNamedSiteUser("c@a.com")
        val userD = userManagement.injectNamedSiteUser("d@a.com")

        val contactList = encryptRemoteAddressBookEntries(userA.keyVault, listOf(userB, userC, userD).map { AddressBookUpdate.Contact(it.user.id, AllowedMessageLevel.ALL) })

        devClient.addAddressBookEntries(userA.user.email, contactList.subList(0, contactList.size))

        val client = AddressBookClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.email)

        val updates = listOf(
            AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.GROUP_ONLY),
            AddressBookUpdate.Contact(userC.user.id, AllowedMessageLevel.BLOCKED)
        )

        val updated = encryptRemoteAddressBookEntries(userA.keyVault, updates)
        val localHash = hashFromRemoteAddressBookEntries(updated)

        val request = UpdateAddressBookRequest(localHash, updated)

        val response = client.update(userA.getUserCredentials(authToken), request)

        assertTrue(response.updated, "Server says updates had no effect")

        val addressBook = devClient.getAddressBook(userA.user.email)

        val expected = listOf(
            updated[0],
            updated[1],
            contactList[2]
        )

        assertAddressBookEquals(expected, addressBook, "Local and remote address books don't match")
    }
}