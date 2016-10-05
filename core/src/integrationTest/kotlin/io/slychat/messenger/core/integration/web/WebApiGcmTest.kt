package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.gcm.GcmClient
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class WebApiGcmTest {
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

    fun checkGCMTokenStatus(user: SiteUser, exists: Boolean) {
        val client = GcmClient(serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(user.email)

        val response = client.isRegistered(user.getUserCredentials(authToken))

        assertEquals(exists, response.isRegistered, "Invalid gcm token status")
    }

    @Test
    fun `gcm isRegistered should return true if token is registered`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        val token = "gcm"

        val deviceId = devClient.addDevice(userA.email, defaultRegistrationId, DeviceState.ACTIVE)

        devClient.registerGcmToken(userA.email, deviceId, token)

        checkGCMTokenStatus(userA, true)
    }

    @Test
    fun `gcm isRegistered should return false if token is not registered`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        checkGCMTokenStatus(userA, false)
    }

    @Test
    fun `gcm unregister should unregister the current device token`() {
        val user = userManagement.injectNamedSiteUser("a@a.com").user
        val token = "gcm"

        val deviceId = devClient.addDevice(user.email, defaultRegistrationId, DeviceState.ACTIVE)

        devClient.registerGcmToken(user.email, deviceId, token)

        val authToken = devClient.createAuthToken(user.email)

        val client = GcmClient(serverBaseUrl, JavaHttpClient())
        client.unregister(user.getUserCredentials(authToken))

        checkGCMTokenStatus(user, false)
    }
}
