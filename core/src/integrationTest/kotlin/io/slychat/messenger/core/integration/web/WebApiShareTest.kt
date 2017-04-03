package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.currentTimestampSeconds
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.ShareClient
import io.slychat.messenger.core.http.api.share.ShareClientImpl
import io.slychat.messenger.core.http.api.storage.FileInfo
import io.slychat.messenger.core.integration.utils.*
import io.slychat.messenger.core.randomPathHash
import io.slychat.messenger.testutils.desc
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebApiShareTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private fun newClient(): ShareClient {
        return ShareClientImpl(serverBaseUrl, JavaHttpClient())
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `acceptShare should accept a file`() {
        val sendingUser = devClient.newNamedAuthUser(userManagement, "a@a.com")
        val receivingUser = devClient.newNamedAuthUser(userManagement, "b@a.com")

        val client = newClient()

        val fileInfo = FileInfo(
            generateFileId(),
            generateShareKey(),
            false,
            1,
            currentTimestampSeconds(),
            currentTimestampSeconds(),
            emptyByteArray(),
            emptyByteArray(),
            10
        )
        devClient.addFile(sendingUser.user.id, fileInfo)

        val request = AcceptShareRequest(
            sendingUser.user.id,
            fileInfo.id,
            fileInfo.shareKey,
            generateFileId(),
            generateShareKey(),
            byteArrayOf(0x77),
            randomPathHash()
        )
        val resp = client.acceptShare(receivingUser.userCredentials, request)

        assertNull(resp.error, "An error occured")

        val ourFileInfo = assertNotNull(devClient.getFileInfo(receivingUser.user.id, request.ourFileId), "File not added to list")

        assertEquals(request.ourShareKey, ourFileInfo.shareKey, "Invalid share key")
        Assertions.assertThat(ourFileInfo.userMetadata).desc("Invalid file info") {
            inHexadecimal()
            isEqualTo(request.userMetadata)
        }
    }
}