package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.accountreset.ResetAccountClient
import io.slychat.messenger.core.http.api.accountreset.ResetAccountRequest
import io.slychat.messenger.core.http.api.accountreset.ResetConfirmCodeRequest
import io.slychat.messenger.core.integration.utils.DevClient
import io.slychat.messenger.core.integration.utils.IsDevServerRunningClassRule
import io.slychat.messenger.core.integration.utils.serverBaseUrl
import io.slychat.messenger.core.integration.utils.SiteUserManagement
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebApiResetAccountTest {
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
    fun `Start Reset Request should succeed when an account exist for the given email`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val request = ResetAccountRequest(user.user.email)
        val response = client.resetAccount(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")
    }

    @Test
    fun `Reset phone Request should succeed when an account exist for the given email and the right code is provided`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val request = ResetAccountRequest(user.user.email)
        val response = client.resetAccount(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")

        val code = "1"
        val confirmRequest = ResetConfirmCodeRequest(user.user.email, code)
        val confirmResponse = client.submitSmsResetCode(confirmRequest)

        assertTrue(confirmResponse.isSuccess, "Update Phone confirm failed: ${response.errorMessage}")
    }

    @Test
    fun `Reset email Request should succeed when an account exist for the given email and the right code is provided`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val request = ResetAccountRequest(user.user.email)
        val response = client.resetAccount(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")

        val code = "1"
        val confirmRequest = ResetConfirmCodeRequest(user.user.email, code)
        val confirmResponse = client.submitEmailResetCode(confirmRequest)

        assertTrue(confirmResponse.isSuccess, "Update Email confirm failed: ${response.errorMessage}")
    }

    @Test
    fun `Full reset request should succeed when an account exist for the given email and the right code is provided`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val request = ResetAccountRequest(user.user.email)
        val response = client.resetAccount(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")

        val code = "1"

        val confirmSmsRequest = ResetConfirmCodeRequest(user.user.email, code)
        val confirmSmsResponse = client.submitSmsResetCode(confirmSmsRequest)

        assertTrue(confirmSmsResponse.isSuccess, "Update Phone confirm failed: ${response.errorMessage}")

        val confirmEmailRequest = ResetConfirmCodeRequest(user.user.email, code)
        val confirmEmailResponse = client.submitEmailResetCode(confirmEmailRequest)

        assertTrue(confirmEmailResponse.isSuccess, "Update Email confirm failed: ${response.errorMessage}")
    }

    @Test
    fun `Start Reset Request should succeed when an account exist for the given phoneNumber`() {
        val user = userManagement.injectNamedSiteUser("a@a.com", "1234567890")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val request = ResetAccountRequest(user.user.phoneNumber)
        val response = client.resetAccount(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")
    }

    @Test
    fun `Start Reset Request should fail when an account does not exist for the given phoneNumber`() {
        val user = userManagement.injectNamedSiteUser("a@a.com", "1234567890")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val phone = "01234567890"

        val request = ResetAccountRequest(phone)
        val response = client.resetAccount(request)

        assertFalse(response.isSuccess, "Update request failed: ${response.errorMessage}")
    }

    @Test
    fun `Start Reset Request should fail when an account does not exist for the given email`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")

        val client = ResetAccountClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val email = "b@b.com"

        val request = ResetAccountRequest(email)
        val response = client.resetAccount(request)

        assertFalse(response.isSuccess, "Update request failed: ${response.errorMessage}")
    }
}