package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.ApiException
import io.slychat.messenger.core.http.api.upload.NewUploadRequest
import io.slychat.messenger.core.http.api.upload.UploadClientImpl
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebApiUploadTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val password = userManagement.defaultPassword
    private val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    private fun getDummyUploadRequest(partCount: Int = 1): NewUploadRequest {
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

    @Before
    fun before() {
        devClient.clear()
    }

    private fun newClient(): UploadClientImpl {
        return UploadClientImpl(serverBaseUrl, "TODO", JavaHttpClient())
    }

    @Test
    fun `newUpload should fail if creds are invalid`() {
        val user = userManagement.injectNewSiteUser()

        val client = newClient()

        assertFailsWith(UnauthorizedException::class) {
            client.newUpload(invalidUserCredentials, getDummyUploadRequest())
        }
    }

    @Test
    fun `newUpload should create a new upload when given valid values`() {
        val user = userManagement.injectNewSiteUser()
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val authToken = devClient.createAuthToken(username, deviceId)

        val client = newClient()

        val request = getDummyUploadRequest()
        val uploadId = request.uploadId
        val fileSize = request.fileSize

        val resp = client.newUpload(user.getUserCredentials(authToken, deviceId), request)

        assertTrue(resp.hadSufficientQuota, "Should have sufficient quota")
        assertEquals(fileSize, resp.quota.usedBytes, "Quota not updated")

        val uploadInfo = assertNotNull(devClient.getUploadInfo(user.user.id, uploadId), "No upload returned from server")

        assertEquals(uploadId, uploadInfo.id, "Invalid id")
        assertEquals(request.fileId, uploadInfo.fileId, "Invalid fileId")
        assertEquals(request.partCount, uploadInfo.parts.size, "Invalid part count")
        assertTrue(Arrays.equals(request.userMetadata, uploadInfo.userMetadata), "Invalid user metadata")
        assertTrue(Arrays.equals(request.fileMetadata, uploadInfo.fileMetadata), "Invalid file metadata")
    }

    @Test
    fun `completeUpload should fail if creds are invalid`() {
        val client = newClient()

        assertFailsWith(UnauthorizedException::class) {
            client.completeUpload(invalidUserCredentials, generateUploadId())
        }
    }
    
    @Test
    fun `completeUpload should complete the upload if all parts are complete`() {
        val user = userManagement.injectNewSiteUser()
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val authToken = devClient.createAuthToken(username, deviceId)

        val client = newClient()

        val request = getDummyUploadRequest()
        val userCredentials = user.getUserCredentials(authToken, deviceId)
        val resp = client.newUpload(userCredentials, request)
        assertTrue(resp.hadSufficientQuota, "Insufficient quota")

        devClient.markPartsAsComplete(user.user.id, request.uploadId)

        client.completeUpload(userCredentials, request.uploadId)

        val fileInfo = assertNotNull(devClient.getFileInfo(user.user.id, request.fileId), "No such file")

        assertEquals(request.fileSize, fileInfo.size, "Invalid fileSize")
        assertTrue(Arrays.equals(request.fileMetadata, fileInfo.fileMetadata), "Invalid file metadata")
        assertTrue(Arrays.equals(request.userMetadata, fileInfo.userMetadata), "Invalid user metadata")
    }

    @Test
    fun `completeUpload should fail if not all parts are complete`() {
        val user = userManagement.injectNewSiteUser()
        val username = user.user.email
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val authToken = devClient.createAuthToken(username, deviceId)

        val client = newClient()

        val request = getDummyUploadRequest(2)
        val userCredentials = user.getUserCredentials(authToken, deviceId)
        val resp = client.newUpload(userCredentials, request)
        assertTrue(resp.hadSufficientQuota, "Insufficient quota")

        val e = assertFailsWith(ApiException::class) {
            client.completeUpload(userCredentials, request.uploadId)
        }

        assertEquals("IncompleteUpload", e.message, "Invalid error message")
    }
}

