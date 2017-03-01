package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.storage.StorageClient
import io.slychat.messenger.core.http.api.storage.StorageClientImpl
import io.slychat.messenger.core.http.api.upload.NewUploadRequest
import io.slychat.messenger.core.http.api.upload.UploadClientImpl
import org.assertj.core.api.Assertions
import org.junit.*
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class FileServerApiStorageTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()

        private var isStorageEnabled = false

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            val devSettings = isDevFileServerRunning()

            isStorageEnabled = devSettings.storageEnabled
        }
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    private fun assumeStorageEnabled() {
        Assume.assumeTrue(isStorageEnabled)
    }

    @Before
    fun before() {
        devClient.clear()
    }

    private fun newClient(): StorageClient {
        return StorageClientImpl(serverBaseUrl, fileServerBaseUrl, JavaHttpClient())
    }

    @Test
    fun `downloadFile should fail if creds are invalid`() {
        assertFailsWith(UnauthorizedException::class) {
            newClient().downloadFile(invalidUserCredentials, generateFileId())
        }
    }

    @Test
    fun `downloadFile should return null if file is missing`() {
        val authUser = devClient.newAuthUser(userManagement)

        val client = newClient()

        assertNull(client.downloadFile(authUser.userCredentials, generateFileId()), "Null should be returned if file doesn't exist")
    }

    private fun uploadFile(userCredentials: UserCredentials, fileData: ByteArray): String {
        val partSize = fileData.size.toLong()

        val client = UploadClientImpl(serverBaseUrl, fileServerBaseUrl, JavaHttpClient())

        val fileId = generateFileId()

        val request = NewUploadRequest(
            generateUploadId(),
            fileId,
            "sk",
            partSize,
            partSize,
            0,
            1,
            byteArrayOf(0x77), byteArrayOf(0x66)
        )

        val newUploadResponse = client.newUpload(userCredentials, request)
        assertTrue(newUploadResponse.hadSufficientQuota, "Insufficient quota")

        client.uploadPart(
            userCredentials,
            request.uploadId,
            1,
            partSize,
            ByteArrayInputStream(fileData),
            AtomicBoolean(),
            null
        )

        return fileId
    }

    private fun randomFileData(): ByteArray {
        val count = randomInt(10, 20)
        val a = ByteArray(count)
        Random().nextBytes(a)
        return a
    }

    //stubbing this out is kind of annoying, so this is just a full upload+download integration test
    @Test
    fun `downloadFile should download an existing file`() {
        assumeStorageEnabled()

        val authUser = devClient.newAuthUser(userManagement)

        val fileData = randomFileData()

        val fileId = uploadFile(authUser.userCredentials, fileData)

        val client = newClient()

        val resp = assertNotNull(client.downloadFile(authUser.userCredentials, fileId), "Missing file")

        val downloadedData = resp.inputStream.use {
            assertEquals(fileData.size.toLong(), resp.contentLength, "Invalid content-length")

            it.readBytes()
        }

        Assertions.assertThat(downloadedData).apply {
            describedAs("Downloaded data should be the same as uploaded data")
            inHexadecimal()
            isEqualTo(fileData)
        }
    }
}