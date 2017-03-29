package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.accountupdate.*
import io.slychat.messenger.core.http.api.registration.RegistrationClient
import io.slychat.messenger.core.integration.utils.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebApiAccountUpdateTest {
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

    @Test
    fun `Update Phone should succeed when right password is provided`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = RegistrationClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val newPhoneNumber = "123453456"

        val request = UpdatePhoneRequest(user.user.email, user.remotePasswordHash.hexify(), newPhoneNumber)
        val response = client.updatePhone(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")

        val remote = assertNotNull(devClient.getUser(user.user.email), "Missing user")

        assertEquals(newPhoneNumber, remote.phoneNumber, "Phone number should be updated on success")
    }

    @Test
    fun `Update Phone should fail when wrong password is provided`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = RegistrationClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val request = UpdatePhoneRequest(user.user.email, "wrongPassword", "1111111111")
        val response = client.updatePhone(request)

        assertFalse(response.isSuccess)

        val remote = assertNotNull(devClient.getUser(user.user.email), "Missing user")

        assertEquals(user.user.phoneNumber, remote.phoneNumber, "Phone number should not be updated on failure")
    }

    @Test
    fun `Update Email should succeed when email is available`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.email)

        val client = AccountUpdateClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val newEmail = "b@b.com"

        val request = UpdateEmailRequest(newEmail)
        val response = client.updateEmail(userA.getUserCredentials(authToken), request)

        assertTrue(response.isSuccess)
        assertEquals(response.accountInfo!!.email, newEmail)
    }

    @Test
    fun `Update Email should fail when email is not available`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user
        userManagement.injectNamedSiteUser("b@b.com").user

        val authToken = devClient.createAuthToken(userA.email)

        val client = AccountUpdateClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val newEmail = "b@b.com"

        val request = UpdateEmailRequest(newEmail)
        val response = client.updateEmail(userA.getUserCredentials(authToken), request)

        assertFalse(response.isSuccess)
    }

    @Test
    fun `Update Name should succeed`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.email)

        val client = AccountUpdateClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val newName = "newName"

        val request = UpdateNameRequest(newName)
        val response = client.updateName(userA.getUserCredentials(authToken), request)

        assertTrue(response.isSuccess)
        assertEquals(response.accountInfo!!.name, newName)
    }

    @Test
    fun `Update Phone should succeed when phone is available`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.email)

        val client = AccountUpdateClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val newPhone = "12345678901"

        val request = RequestPhoneUpdateRequest(newPhone)
        val userCredentials = userA.getUserCredentials(authToken)
        val response = client.requestPhoneUpdate(userCredentials, request)

        assertTrue(response.isSuccess)

        val secondRequest = ConfirmPhoneNumberRequest("1")
        val secondResponse = client.confirmPhoneNumber(userCredentials, secondRequest)

        assertTrue(secondResponse.isSuccess)
        assertEquals(secondResponse.accountInfo!!.phoneNumber, newPhone)
    }

    @Test
    fun `Update Phone should fail when phone is not available`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user
        userManagement.injectNamedSiteUser("b@b.com", "2222222222").user

        val authToken = devClient.createAuthToken(userA.email)

        val client = AccountUpdateClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val newPhone = "2222222222"

        val request = RequestPhoneUpdateRequest(newPhone)
        val response = client.requestPhoneUpdate(userA.getUserCredentials(authToken), request)

        assertFalse(response.isSuccess, "Update failed")
    }
}