package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.storage.FileInfo
import io.slychat.messenger.core.http.api.storage.StorageClient
import io.slychat.messenger.core.http.api.storage.StorageClientImpl
import org.assertj.core.api.Assertions.assertThat
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

    private fun newClient(): StorageClient {
        return StorageClientImpl(serverBaseUrl, fileServerBaseUrl, JavaHttpClient())
    }

    private fun addDummyFile(userId: UserId, lastUpdateVersion: Int): FileInfo {
        val fileInfo = FileInfo(
            generateFileId(),
            "sk",
            false,
            lastUpdateVersion,
            currentTimestampSeconds(),
            currentTimestampSeconds(),
            emptyByteArray(),
            emptyByteArray(),
            10L
        )

        devClient.addFile(userId, fileInfo)

        return fileInfo
    }

    @Test
    fun `quota should return the user's quota`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(username, deviceId)

        val client = newClient()

        val quota = client.getQuota(user.getUserCredentials(authToken, deviceId))
        val expected = devClient.getQuota(user.user.id)

        assertEquals(expected, quota, "Invalid quota")
    }

    @Test
    fun `getFileList should return the user's file list`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val lastUpdateVersion = 1

        devClient.addFileListVersion(user.user.id, lastUpdateVersion)

        val presentFileInfo = FileInfo(
            generateFileId(),
            "sk",
            false,
            lastUpdateVersion,
            currentTimestampSeconds(),
            currentTimestampSeconds(),
            emptyByteArray(),
            emptyByteArray(),
            10L
        )

        val deletedFileInfo = FileInfo(
            generateFileId(),
            "sk",
            true,
            lastUpdateVersion,
            currentTimestampSeconds(),
            currentTimestampSeconds(),
            emptyByteArray(),
            null,
            0
        )

        devClient.addFile(user.user.id, presentFileInfo)
        devClient.addFile(user.user.id, deletedFileInfo)

        val authToken = devClient.createAuthToken(username, deviceId)

        val client = newClient()

        val resp = client.getFileList(user.getUserCredentials(authToken, deviceId), 0)

        assertThat(resp.files).apply {
            describedAs("Should contain the user's files")
            containsOnly(presentFileInfo, deletedFileInfo)
        }

        assertEquals(resp.version, lastUpdateVersion, "Invalid file list version")
    }

    @Test
    fun `updateMetadata should update the user's metadata`() {
        val user = userManagement.injectNamedSiteUser("a@a.com")
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val lastUpdateVersion = 1

        devClient.addFileListVersion(user.id, lastUpdateVersion)

        val fileInfo = addDummyFile(user.id, 1)

        val authToken = devClient.createAuthToken(username, deviceId)

        val client = newClient()

        val newMetadata = byteArrayOf(0x77)

        val userCredentials = user.getUserCredentials(authToken, deviceId)
        val updateResp = client.updateMetadata(userCredentials, fileInfo.id, newMetadata)

        assertEquals(lastUpdateVersion + 1, updateResp.newVersion, "Invalid new version")

        val getResp = client.getFileInfo(userCredentials, fileInfo.id)

        assertThat(getResp.fileInfo.userMetadata).apply {
            describedAs("Metadata not updated")
            inHexadecimal()
            isEqualTo(newMetadata)
        }
    }
}