package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.upload.NewUploadRequest
import io.slychat.messenger.core.http.api.upload.UploadClientImpl
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertTrue

class FileServerApiUploadTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()

        @ClassRule
        @JvmField
        val isFileServerRunning = IsDevFileServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val password = userManagement.defaultPassword
    private val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    @Before
    fun before() {
        devClient.clear()
    }

    private fun newClient(): UploadClientImpl {
        return UploadClientImpl(serverBaseUrl, fileServerBaseUrl, JavaHttpClient())
    }

    private fun getNewUploadRequest(partCount: Int = 1): NewUploadRequest {
        return NewUploadRequest(
            generateUploadId(),
            generateFileId(),
            10L * partCount,
            10,
            0,
            partCount,
            byteArrayOf(0x77),
            byteArrayOf(0x66), "sk"
        )
    }

    @Test
    fun `single upload should work for a valid upload and part number`() {
        val user = userManagement.injectNewSiteUser()
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val authToken = devClient.createAuthToken(username, deviceId)

        val userCredentials = user.getUserCredentials(authToken, deviceId)

        val client = newClient()

        val request = getNewUploadRequest()
        val resp = client.newUpload(userCredentials, request)
        assertTrue(resp.hadSufficientQuota, "Insufficient quota")

        val inputStream = DummyInputStream(request.partSize)

        client.uploadPart(
            userCredentials,
            request.uploadId,
            1,
            request.partSize,
            inputStream,
            null
        )
    }
}