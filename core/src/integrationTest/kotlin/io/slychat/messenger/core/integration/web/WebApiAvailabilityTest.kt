package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.availability.AvailabilityClient
import io.slychat.messenger.core.integration.utils.DevClient
import io.slychat.messenger.core.integration.utils.SiteUserManagement
import io.slychat.messenger.core.integration.utils.serverBaseUrl
import io.slychat.messenger.core.randomEmailAddress
import io.slychat.messenger.core.randomPhoneNumber
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebApiAvailabilityTest {
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
    fun `checkEmailAvailability should return false if the email is not in use`() {
        val client = AvailabilityClient(serverBaseUrl, JavaHttpClient())

        assertTrue(client.checkEmailAvailability(randomEmailAddress()), "Email should be available")
    }

    @Test
    fun `checkEmailAvailability should return true if the email is in use`() {
        val siteUser = userManagement.injectNewSiteUser()
        val client = AvailabilityClient(serverBaseUrl, JavaHttpClient())

        assertFalse(client.checkEmailAvailability(siteUser.user.email), "Email should not be available")
    }

    @Test
    fun `checkPhoneNumberAvailability should return false if the email is not in use`() {
        val client = AvailabilityClient(serverBaseUrl, JavaHttpClient())

        assertTrue(client.checkPhoneNumberAvailability(randomPhoneNumber()), "Phone number should be available")
    }

    @Test
    fun `checkPhoneNumberAvailability should return true if the email is in use`() {
        val siteUser = userManagement.injectNewSiteUser()
        val client = AvailabilityClient(serverBaseUrl, JavaHttpClient())

        assertFalse(client.checkPhoneNumberAvailability(siteUser.user.phoneNumber), "Phone number should not be available")
    }
}