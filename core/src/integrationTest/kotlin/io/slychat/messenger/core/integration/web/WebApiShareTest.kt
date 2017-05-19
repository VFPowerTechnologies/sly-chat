package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateShareKey
import io.slychat.messenger.core.currentTimestampSeconds
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.ShareClient
import io.slychat.messenger.core.http.api.share.ShareClientImpl
import io.slychat.messenger.core.http.api.share.ShareInfo
import io.slychat.messenger.core.http.api.storage.FileInfo
import io.slychat.messenger.core.integration.utils.*
import io.slychat.messenger.core.randomPathHash
import io.slychat.messenger.testutils.desc
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    private fun randomFileInfo(): FileInfo {
        return FileInfo(
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
    }

    @Test
    fun `acceptShare should accept a file`() {
        val sendingUser = devClient.newNamedAuthUser(userManagement, "a@a.com")
        val receivingUser = devClient.newNamedAuthUser(userManagement, "b@a.com")

        val client = newClient()

        val fileInfo = randomFileInfo()
        devClient.addFile(sendingUser.user.id, fileInfo)

        val ourFileId = generateFileId()
        val ourShareKey = generateShareKey()
        val userMetadata = byteArrayOf(0x77)
        val request = AcceptShareRequest(
            sendingUser.user.id,
            listOf(
                ShareInfo(
                    fileInfo.id,
                    ourFileId,
                    fileInfo.shareKey,
                    ourShareKey,
                    userMetadata,
                    randomPathHash()
                )
            )
        )
        val resp = client.acceptShare(receivingUser.userCredentials, request)

        assertThat(resp.errors).desc("Should not have any errors") {
            isEmpty()
        }

        val ourFileInfo = assertNotNull(devClient.getFileInfo(receivingUser.user.id, ourFileId), "File not added to list")

        assertEquals(ourShareKey, ourFileInfo.shareKey, "Invalid share key")
        Assertions.assertThat(ourFileInfo.userMetadata).desc("Invalid file info") {
            inHexadecimal()
            isEqualTo(userMetadata)
        }
    }

    @Test
    fun `acceptShare should be idempotent`() {
        val sendingUser = devClient.newNamedAuthUser(userManagement, "a@a.com")
        val receivingUser = devClient.newNamedAuthUser(userManagement, "b@a.com")

        val client = newClient()

        val fileInfo = randomFileInfo()
        devClient.addFile(sendingUser.user.id, fileInfo)

        val ourFileId = generateFileId()
        val shareInfo = ShareInfo(
            fileInfo.id,
            ourFileId,
            fileInfo.shareKey,
            generateShareKey(),
            byteArrayOf(0x77),
            randomPathHash()
        )

        val request = AcceptShareRequest(
            sendingUser.user.id,
            listOf(shareInfo)
        )
        val resp = client.acceptShare(receivingUser.userCredentials, request)

        assertThat(resp.errors).desc("Should not have any errors") {
            isEmpty()
        }

        val request2 = AcceptShareRequest(
            sendingUser.user.id,
            listOf(shareInfo.copy(ourFileId = generateFileId()))
        )

        val resp2 = client.acceptShare(receivingUser.userCredentials, request2)

        assertThat(resp.errors).desc("Should not have any errors") {
            isEmpty()
        }

        assertThat(resp2.successes).desc("Should return the original fileId") {
            containsEntry(shareInfo.theirFileId, ourFileId)
        }

        assertNotNull(devClient.getFileInfo(receivingUser.user.id, ourFileId), "File not added to list")
    }
}