package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationsClient
import io.slychat.messenger.core.http.api.pushnotifications.UnregisterRequest
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class WebApiPushNotificationsTest {
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
        val client = PushNotificationsClient(serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(user.email)

        val response = client.isRegistered(user.getUserCredentials(authToken))

        assertEquals(exists, response.isRegistered, "Invalid gcm token status")
    }

    @Test
    fun `isRegistered should return true if token is registered`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        val token = "gcm"

        val deviceId = devClient.addDevice(userA.email, defaultRegistrationId, DeviceState.ACTIVE)

        devClient.registerPushNotificationToken(userA.email, deviceId, token, PushNotificationService.GCM, false)

        checkGCMTokenStatus(userA, true)
    }

    @Test
    fun `isRegistered should return false if token is not registered`() {
        val userA = userManagement.injectNamedSiteUser("a@a.com").user

        checkGCMTokenStatus(userA, false)
    }

    @Test
    fun `unregister should unregister the given device token`() {
        val user = userManagement.injectNamedSiteUser("a@a.com").user
        val token = "gcm"

        val deviceId = devClient.addDevice(user.email, defaultRegistrationId, DeviceState.ACTIVE)

        devClient.registerPushNotificationToken(user.email, deviceId, token, PushNotificationService.GCM, false)

        val client = PushNotificationsClient(serverBaseUrl, JavaHttpClient())
        client.unregister(UnregisterRequest(SlyAddress(user.id, deviceId), token))

        checkGCMTokenStatus(user, false)
    }
}
