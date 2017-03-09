package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.ciphers.AES256GCMCipher
import io.slychat.messenger.core.kb
import io.slychat.messenger.core.mb
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class FilesUtilsTest {
    private val cipher = AES256GCMCipher()

    private fun assertPartSanity(fileSize: Long, encryptedChunkSize: Int, actual: ArrayList<UploadPart>) {
        assertEquals(fileSize, actual.fold(0L) { i, p -> i + p.localSize }, "Total part localSize should match local data size")
        Upload.verifyParts(actual)

        assertEquals(
            getRemoteFileSize(cipher, fileSize, encryptedChunkSize),
            actual.fold(0L) { i, p -> i + p.remoteSize },
            "Total part remoteSize should match remote data size"
        )
    }

    @Test
    fun `calcUploadParts should calculate the proper size for multiple parts`() {
        val expected = listOf(
            UploadPart(n=1, offset=0, localSize=5241760, remoteSize=5242880, isComplete=false),
            UploadPart(n=2, offset=5241760, localSize=5241760, remoteSize=5242880, isComplete=false),
            UploadPart(n=3, offset=10483520, localSize = 2099392, remoteSize=2099868, isComplete=false)
        )

        val minPartSize = 5.mb
        val fileSize = 12L.mb
        val encryptedChunkSize = 128.kb
        val actual = calcUploadParts(cipher, fileSize, encryptedChunkSize, minPartSize)

        assertPartSanity(fileSize, encryptedChunkSize, actual)

        assertThat(actual).apply {
            describedAs("Should contain the proper measurements")
            containsExactlyElementsOf(expected)
        }
    }

    @Test
    fun `calcUploadParts should calculate the proper size when given an even plaintext chunk size`() {
        val encryptedChunkSize = 128.kb
        val fileSize = cipher.getInputSizeForOutput(encryptedChunkSize) * 90L
        val actual = calcUploadParts(cipher, fileSize, 128.kb, 5.mb)

        assertPartSanity(fileSize, encryptedChunkSize, actual)

        assertThat(actual).apply {
            describedAs("Should only contain a single part")
            hasSize(3)
        }
    }

    @Test
    fun `calcUploadParts should calc the proper size when given a size evenly divisible into the min part size`() {
        val encryptedChunkSize = 128.kb
        val fileSize = cipher.getInputSizeForOutput(encryptedChunkSize) * 40L
        val actual = calcUploadParts(cipher, fileSize, encryptedChunkSize, 5.mb)

        assertThat(actual).apply {
            describedAs("Should only contain a single part")
            hasSize(1)
        }

        assertPartSanity(fileSize, encryptedChunkSize, actual)
    }
}