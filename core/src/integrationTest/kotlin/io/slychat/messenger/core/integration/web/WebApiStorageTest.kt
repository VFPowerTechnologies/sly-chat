package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.StorageClientImpl
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class WebApiStorageTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val password = userManagement.defaultPassword
    private val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `quota should return the user's quota`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(username, deviceId)

        val client = StorageClientImpl(serverBaseUrl, JavaHttpClient())

        val quota = client.getQuota(user.getUserCredentials(authToken, deviceId))
        val expected = devClient.getQuota(user.user.id)

        assertEquals(expected, quota, "Invalid quota")
    }
}