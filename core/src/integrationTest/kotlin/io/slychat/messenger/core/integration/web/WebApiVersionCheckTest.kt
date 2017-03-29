package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.versioncheck.ClientVersionClientImpl
import io.slychat.messenger.core.integration.utils.DevClient
import io.slychat.messenger.core.integration.utils.IsDevServerRunningClassRule
import io.slychat.messenger.core.integration.utils.serverBaseUrl
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebApiVersionCheckTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `version check should ignore SNAPSHOT versions`() {
        val client = ClientVersionClientImpl(serverBaseUrl, JavaHttpClient())

        assertTrue(client.check("0.0.0-SNAPSHOT").isLatest, "SNAPSHOT versions should always be valid")
    }

    @Test
    fun `version check should return false for an older version`() {
        val client = ClientVersionClientImpl(serverBaseUrl, JavaHttpClient())

        assertFalse(client.check("0.0.0").isLatest, "Version should be outdated")
    }

    @Test
    fun `version check should return true for an up-to-date version`() {
        val latestVersion = devClient.getLatestVersion()

        val client = ClientVersionClientImpl(serverBaseUrl, JavaHttpClient())

        assertTrue(client.check(latestVersion).isLatest, "Latest version not accepted as up to date")
    }

    @Test
    fun `version check should always return latest version in outdated response`() {
        val latestVersion = devClient.getLatestVersion()

        val client = ClientVersionClientImpl(serverBaseUrl, JavaHttpClient())

        assertEquals(latestVersion, client.check("0.0.0").latestVersion, "Version should be included in response")
    }

    @Test
    fun `version check should always return latest version in up to date response`() {
        val latestVersion = devClient.getLatestVersion()

        val client = ClientVersionClientImpl(serverBaseUrl, JavaHttpClient())

        assertEquals(latestVersion, client.check(latestVersion).latestVersion, "Version should be included in response")
    }
}