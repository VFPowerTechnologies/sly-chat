package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.ApiException
import io.slychat.messenger.core.http.api.storage.FileInfo
import io.slychat.messenger.core.http.api.upload.*
import io.slychat.messenger.core.integration.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import kotlin.test.*

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

    private fun randomNewUploadRequest(partCount: Int = 1, partSize: Long = 10, pathHash: String = randomPathHash()): NewUploadRequest {
        return NewUploadRequest(
            generateUploadId(),
            generateFileId(),
            "sk",
            partSize * partCount,
            partSize,
            0,
            partCount,
            byteArrayOf(0x77),
            byteArrayOf(0x66),
            pathHash
        )
    }

    private fun newClient(): UploadClientImpl {
        return UploadClientImpl(serverBaseUrl, fileServerBaseUrl, JavaHttpClient())
    }

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `newUpload should fail if creds are invalid`() {
        val client = newClient()

        assertFailsWith(UnauthorizedException::class) {
            client.newUpload(invalidUserCredentials, randomNewUploadRequest())
        }
    }

    @Test
    fun `newUpload should create a new upload when given valid values`() {
        val authUser = devClient.newAuthUser(userManagement)

        val client = newClient()

        val request = randomNewUploadRequest()
        val uploadId = request.uploadId
        val fileSize = request.fileSize

        val resp = client.newUpload(authUser.userCredentials, request)

        assertNull(resp.error, "Should have sufficient quota")
        assertEquals(fileSize, resp.quota.usedBytes, "Quota not updated")

        val uploadInfo = assertNotNull(client.getUpload(authUser.userCredentials, uploadId).upload, "No upload returned from server")

        assertEquals(uploadId, uploadInfo.id, "Invalid id")
        assertEquals(request.fileId, uploadInfo.fileId, "Invalid fileId")
        assertEquals(request.partCount, uploadInfo.parts.size, "Invalid part count")
        assertTrue(Arrays.equals(request.userMetadata, uploadInfo.userMetadata), "Invalid user metadata")
        assertTrue(Arrays.equals(request.fileMetadata, uploadInfo.fileMetadata), "Invalid file metadata")
    }

    @Test
    fun `newUpload should fail if user has unsufficient quota`() {
        val authUser = devClient.newAuthUser(userManagement)
        val quota = devClient.getQuota(authUser.user.id)

        val client = newClient()

        val request = randomNewUploadRequest(partSize = quota.maxBytes + 1)

        val resp = client.newUpload(authUser.userCredentials, request)
        assertEquals(NewUploadError.INSUFFICIENT_QUOTA, resp.error, "Expected insufficient quota")
    }

    @Test
    fun `newUpload should fail if the user has an existing upload with the same path`() {
        val authUser = devClient.newAuthUser(userManagement)

        val client = newClient()

        val request = randomNewUploadRequest()

        val resp1 = client.newUpload(authUser.userCredentials, request)
        assertNull(resp1.error, "An error occured")

        val request2 = randomNewUploadRequest(pathHash = request.pathHash)
        val resp2 = client.newUpload(authUser.userCredentials, request2)
        assertEquals(NewUploadError.DUPLICATE_FILE, resp2.error, "Should not allow duplicate paths")
    }

    @Test
    fun `newUpload should fail if the user has an existing file with the same path`() {
        val authUser = devClient.newAuthUser(userManagement)

        val pathHash = randomPathHash()
        devClient.addFile(
            authUser.user.id,
            FileInfo(
                generateFileId(),
                generateShareKey(),
                false,
                1,
                1,
                2,
                byteArrayOf(0x66),
                byteArrayOf(0x77),
                10L
            ),
            pathHash
        )

        val client = newClient()
        val request = randomNewUploadRequest(pathHash = pathHash)
        val resp = client.newUpload(authUser.userCredentials, request)

        assertEquals(NewUploadError.DUPLICATE_FILE, resp.error, "Should fail with duplicate file error")
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
        val authUser = devClient.newAuthUser(userManagement)
        val user = authUser.user

        val client = newClient()

        val request = randomNewUploadRequest()
        val resp = client.newUpload(authUser.userCredentials, request)
        assertNull(resp.error, "Insufficient quota")

        devClient.markPartsAsComplete(user.user.id, request.uploadId)

        client.completeUpload(authUser.userCredentials, request.uploadId)

        val fileInfo = assertNotNull(devClient.getFileInfo(user.user.id, request.fileId), "No such file")

        assertEquals(request.fileSize, fileInfo.size, "Invalid fileSize")
        assertTrue(Arrays.equals(request.fileMetadata, fileInfo.fileMetadata), "Invalid file metadata")
        assertTrue(Arrays.equals(request.userMetadata, fileInfo.userMetadata), "Invalid user metadata")
    }

    @Test
    fun `completeUpload should fail if not all parts are complete`() {
        val authUser = devClient.newAuthUser(userManagement)

        val client = newClient()

        val request = randomNewUploadRequest(2)
        val resp = client.newUpload(authUser.userCredentials, request)
        assertNull(resp.error, "Insufficient quota")

        val e = assertFailsWith(ApiException::class) {
            client.completeUpload(authUser.userCredentials, request.uploadId)
        }

        assertEquals("INCOMPLETE_UPLOAD", e.message, "Invalid error message")
    }

    @Test
    fun `getUploads should return nothing if no uploads are pending`() {
        val authUser = devClient.newAuthUser(userManagement)
        val client = newClient()

        assertThat(client.getUploads(authUser.userCredentials).uploads).apply {
            describedAs("Should be empty")
            isEmpty()
        }
    }

    private fun newUpload(client: UploadClient, userCredentials: UserCredentials): String {
        val request = randomNewUploadRequest(1)
        val resp = client.newUpload(userCredentials, request)
        assertNull(resp.error, "Insufficient quota")

        return request.uploadId
    }

    @Test
    fun `getUploads should return all uploads`() {
        val authUser = devClient.newAuthUser(userManagement)
        val client = newClient()

        val request = randomNewUploadRequest(2)
        val resp = client.newUpload(authUser.userCredentials, request)
        assertNull(resp.error, "Insufficient quota")

        val expected = UploadInfo(
            request.uploadId,
            authUser.deviceId,
            request.fileId,
            request.fileSize,
            request.userMetadata,
            request.fileMetadata,
            listOf(
                UploadPartInfo(1, request.partSize, false),
                UploadPartInfo(2, request.partSize, false)
            )
        )

        val getUploadsResponse = client.getUploads(authUser.userCredentials)

        assertThat(getUploadsResponse.uploads).apply {
            describedAs("Should match uploads")
            containsOnly(expected)
        }
    }

    @Test
    fun `cancel should cancel an inactive upload`() {
        val authUser = devClient.newAuthUser(userManagement)
        val client = newClient()
        val uploadId = newUpload(client, authUser.userCredentials)

        client.cancel(authUser.userCredentials, uploadId)

        assertNull(client.getUpload(authUser.userCredentials, uploadId).upload, "Upload not cancelled")
    }

    @Test
    fun `cancel should be idempotent`() {
        val authUser = devClient.newAuthUser(userManagement)
        val client = newClient()
        val uploadId = newUpload(client, authUser.userCredentials)

        client.cancel(authUser.userCredentials, uploadId)
        client.cancel(authUser.userCredentials, uploadId)
    }
}

