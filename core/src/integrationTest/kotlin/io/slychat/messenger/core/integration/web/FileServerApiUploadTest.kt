package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.upload.NewUploadRequest
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.http.api.upload.UploadClientImpl
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileServerApiUploadTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()

        private var isStorageEnabled = false

        private var multiPartSize: Long = 0

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            val devSettings = isDevFileServerRunning()

            isStorageEnabled = devSettings.storageEnabled
            //min part size for s3 (excluding final part) is 5mb
            multiPartSize = if (isStorageEnabled) 5242880L else 10L
        }
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    @Before
    fun before() {
        devClient.clear()
    }

    private fun newClient(): UploadClientImpl {
        return UploadClientImpl(serverBaseUrl, fileServerBaseUrl, JavaHttpClient())
    }

    private fun getSingleNewUploadRequest(): NewUploadRequest {
        val partSize = 10L

        return NewUploadRequest(
            generateUploadId(),
            generateFileId(),
            "sk",
            partSize,
            partSize,
            0,
            1,
            byteArrayOf(0x77), byteArrayOf(0x66)
        )
    }

    private fun getMultiNewUploadRequest(partCount: Int = 1): NewUploadRequest {
        return NewUploadRequest(
            generateUploadId(),
            generateFileId(),
            "sk",
            multiPartSize * partCount,
            multiPartSize,
            0,
            partCount,
            byteArrayOf(0x77), byteArrayOf(0x66)
        )
    }

    private fun uploadPart(client: UploadClient, userCredentials: UserCredentials, uploadId: String, partNumber: Int, partSize: Long) {
        val inputStream = DummyInputStream(partSize)
        val md5InputStream = MD5InputStream(inputStream)

        val resp = client.uploadPart(
            userCredentials,
            uploadId,
            partNumber,
            partSize,
            md5InputStream,
            AtomicBoolean(),
            null
        )

        assertEquals(md5InputStream.digestString, resp.checksum, "Invalid checksum")
    }

    @Test
    fun `upload should fail if creds are invalid`() {
        val client = newClient()

        assertFailsWith(UnauthorizedException::class) {
            uploadPart(client, invalidUserCredentials, generateUploadId(), 1, 10)
        }
    }

    @Test
    fun `single upload should work for a valid upload and part number`() {
        val authUser = devClient.newAuthUser(userManagement)
        val userCredentials = authUser.userCredentials

        val client = newClient()

        val request = getSingleNewUploadRequest()
        val newUploadResponse = client.newUpload(userCredentials, request)
        assertTrue(newUploadResponse.hadSufficientQuota, "Insufficient quota")

        uploadPart(client, userCredentials, request.uploadId, 1, request.partSize)
    }

    @Test
    fun `multipart upload should work for a valid upload and part numbers (even part sizes)`() {
        val authUser = devClient.newAuthUser(userManagement)
        val userCredentials = authUser.userCredentials

        val client = newClient()

        val request = getMultiNewUploadRequest(2)
        val newUploadResponse = client.newUpload(userCredentials, request)
        assertTrue(newUploadResponse.hadSufficientQuota, "Insufficient quota")

        for (i in 1..2)
            uploadPart(client, userCredentials, request.uploadId, i, request.partSize)

        client.completeUpload(userCredentials, request.uploadId)
    }

    @Test
    fun `multipart upload should work for a valid upload and part numbers (uneven part sizes)`() {
        val authUser = devClient.newAuthUser(userManagement)
        val userCredentials = authUser.userCredentials

        val client = newClient()

        val finalPartSize = 5L

        val request = NewUploadRequest(
            generateUploadId(),
            generateFileId(),
            "sk",
            multiPartSize + finalPartSize,
            multiPartSize,
            finalPartSize,
            2,
            byteArrayOf(0x77), byteArrayOf(0x66)
        )

        val newUploadResponse = client.newUpload(userCredentials, request)
        assertTrue(newUploadResponse.hadSufficientQuota, "Insufficient quota")

        uploadPart(client, userCredentials, request.uploadId, 1, request.partSize)
        uploadPart(client, userCredentials, request.uploadId, 2, request.finalPartSize)

        client.completeUpload(userCredentials, request.uploadId)
    }
}