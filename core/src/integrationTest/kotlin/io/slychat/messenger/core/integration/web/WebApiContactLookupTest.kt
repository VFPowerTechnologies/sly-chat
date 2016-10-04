package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.contacts.ContactLookupClient
import io.slychat.messenger.core.http.api.contacts.FindAllByIdRequest
import io.slychat.messenger.core.http.api.contacts.FindContactRequest
import io.slychat.messenger.core.http.api.contacts.FindLocalContactsRequest
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.randomUserId
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebApiContactLookupTest {
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

    private fun SiteUser.toContactInfo(): ContactInfo =
        ContactInfo(id, username, name, AllowedMessageLevel.ALL, phoneNumber, publicKey)

    @Test
    fun `new contact fetch from email should return the contact information`() {
        val siteUser = userManagement.injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.id, siteUser.user.username, siteUser.user.name, AllowedMessageLevel.ALL, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactLookupClient(serverBaseUrl, JavaHttpClient())

        val contactResponseEmail = client.find(siteUser.getUserCredentials(authToken), FindContactRequest(siteUser.user.username, null))
        assertTrue(contactResponseEmail.isSuccess)

        val receivedEmailContactInfo = contactResponseEmail.contactInfo!!

        assertEquals(contactDetails, receivedEmailContactInfo.toCore(AllowedMessageLevel.ALL))
    }

    @Test
    fun `new contact fetch from phone should return the contact information`() {
        val siteUser = userManagement.injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.id, siteUser.user.username, siteUser.user.name, AllowedMessageLevel.ALL, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactLookupClient(serverBaseUrl, JavaHttpClient())

        val contactResponse = client.find(siteUser.getUserCredentials(authToken), FindContactRequest(null, siteUser.user.phoneNumber))
        assertTrue(contactResponse.isSuccess)

        val receivedContactInfo = contactResponse.contactInfo!!

        assertEquals(contactDetails, receivedContactInfo.toCore(AllowedMessageLevel.ALL))
    }

    @Test
    fun `findLocalContacts should find matches for both phone number and emails`() {
        val bPhoneNumber = "15555555555"
        val userA = userManagement.injectNamedSiteUser("a@a.com").user
        val userB = userManagement.injectNamedSiteUser("b@a.com", bPhoneNumber).user
        val userC = userManagement.injectNamedSiteUser("c@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactLookupClient(serverBaseUrl, JavaHttpClient())

        val platformContacts = listOf(
            PlatformContact("B", listOf(userC.username), listOf()),
            PlatformContact("C", listOf(), listOf(bPhoneNumber))
        )
        val request = FindLocalContactsRequest(platformContacts)
        val response = client.findLocalContacts(userA.getUserCredentials(authToken), request)

        val expected = listOf(
            userB.toContactInfo(),
            userC.toContactInfo()
        )

        assertEquals(expected, response.contacts.sortedBy { it.email }.map { it.toCore(AllowedMessageLevel.ALL) })
    }

    @Test
    fun `fetchMultiContactInfoById should fetch users with the given ids`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user
        val userB = userManagement.injectNamedSiteUser("b@a.com").user
        val userC = userManagement.injectNamedSiteUser("c@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactLookupClient(serverBaseUrl, JavaHttpClient())

        val request = FindAllByIdRequest(listOf(userB.id, userC.id))
        val response = client.findAllById(userA.getUserCredentials(authToken), request)

        val expected = listOf(
            userB.toContactInfo(),
            userC.toContactInfo()
        )

        assertEquals(expected, response.contacts.sortedBy { it.email }.map { it.toCore(AllowedMessageLevel.ALL) })
    }

    @Test
    fun `fetchContactInfoById should return valid contact info for an existing user`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user
        val userB = userManagement.injectNamedSiteUser("b@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactLookupClient(serverBaseUrl, JavaHttpClient())

        val response = client.findById(userA.getUserCredentials(authToken), userB.id)

        assertEquals(userB.toContactInfo(), response.contactInfo?.toCore(AllowedMessageLevel.ALL), "Invalid contact info")
    }

    @Test
    fun `fetchContactInfoById should return null for a non-existent user`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactLookupClient(serverBaseUrl, JavaHttpClient())

        val response = client.findById(userA.getUserCredentials(authToken), randomUserId())

        assertNull(response.contactInfo, "Should be not return contact info")
    }
}