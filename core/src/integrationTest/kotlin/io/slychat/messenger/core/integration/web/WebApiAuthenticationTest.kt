package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.crypto.randomRegistrationId
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationRequest
import io.slychat.messenger.core.http.api.authentication.AuthenticationResponse
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.randomAuthToken
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebApiAuthenticationTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val defaultRegistrationId = 12345

    @Before
    fun before() {
        devClient.clear()
    }

    fun sendAuthRequestForUser(userA: GeneratedSiteUser, deviceId: Int): AuthenticationResponse {
        val username = userA.user.username

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        val paramsApiResult = client.getParams(username)
        assertTrue(paramsApiResult.isSuccess, "Unable to fetch params")

        val csrfToken = paramsApiResult.params!!.csrfToken
        val authRequest = AuthenticationRequest(username, userA.remotePasswordHash.hexify(), csrfToken, defaultRegistrationId, deviceId)

        return client.auth(authRequest)
    }

    @Test
    fun `authentication request should succeed when given a valid username and password hash for an existing device`() {
        val userA = userManagement.injectNewSiteUser()
        val siteUser = userA.user
        val username = siteUser.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authApiResult = sendAuthRequestForUser(userA, deviceId)
        assertTrue(authApiResult.isSuccess, "auth failed: ${authApiResult.errorMessage}")

        val receivedSerializedKeyVault = authApiResult.data!!.keyVault

        assertEquals(siteUser.keyVault, receivedSerializedKeyVault)
    }

    @Test
    fun `authentication request should return other active devices for the current user`() {
        val userA = userManagement.injectNewSiteUser()
        val siteUser = userA.user
        val username = siteUser.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        devClient.addDevice(username, defaultRegistrationId, DeviceState.INACTIVE)
        devClient.addDevice(username, defaultRegistrationId, DeviceState.PENDING)
        val activeDeviceRegistrationId = randomRegistrationId()
        val activeDeviceId = devClient.addDevice(username, activeDeviceRegistrationId, DeviceState.ACTIVE)

        val authApiResult = sendAuthRequestForUser(userA, deviceId)
        assertTrue(authApiResult.isSuccess, "auth failed: ${authApiResult.errorMessage}")

        val authData = authApiResult.data!!

        Assertions.assertThat(authData.otherDevices).apply {
            `as`("Should only list active devices")
            containsOnly(DeviceInfo(activeDeviceId, activeDeviceRegistrationId))
        }
    }

    fun runMaxDeviceTest(state: DeviceState) {
        val userA = userManagement.injectNewSiteUser()
        val username = userA.user.username
        val maxDevices = devClient.getMaxDevices()

        for (i in 0..maxDevices-1)
            devClient.addDevice(username, 12345, state)

        val response = sendAuthRequestForUser(userA, 0)

        assertFalse(response.isSuccess, "Auth succeeded")
        assertTrue("too many registered devices" in response.errorMessage!!.toLowerCase())
    }

    @Test
    fun `attempting to authenticate with active devices maxed out should fail`() {
        runMaxDeviceTest(DeviceState.ACTIVE)
    }

    @Test
    fun `attempting to authenticate with pending devices maxed out should fail`() {
        runMaxDeviceTest(DeviceState.PENDING)
    }

    @Ignore("disabled")
    @Test
    fun `token refresh should succeed if the token is still valid`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val authToken = devClient.createAuthToken(siteUser.user.username, deviceId)

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        val response = client.refreshToken(siteUser.getUserCredentials(authToken))

        val serverSideToken = devClient.getAuthToken(username, deviceId)

        assertEquals(response.authToken, serverSideToken, "Invalid token returned")
    }

    @Ignore("disabled")
    @Test
    fun `token refresh should fail if the token is invalid or expired`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.username

        devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        assertFailsWith(UnauthorizedException::class) {
            client.refreshToken(siteUser.getUserCredentials(randomAuthToken()))
        }
    }
}