package io.slychat.messenger.services.files

import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.crypto.ciphers.AES256GCMCipher
import io.slychat.messenger.core.kb
import io.slychat.messenger.core.mb
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.core.rx.observable
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.TestException
import io.slychat.messenger.testutils.testSubscriber
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class FilesUtilsTest {
    private val scheduler = TestScheduler()

    private val cipher = AES256GCMCipher()

    private fun assertPartLocalSizeSanity(fileSize: Long, actual: List<UploadPart>) {
        assertEquals(fileSize, actual.fold(0L) { i, p -> i + p.localSize }, "Total part localSize should match local data size")
        Upload.verifyParts(actual)
    }

    private fun assertPartSanity(fileSize: Long, encryptedChunkSize: Int, actual: List<UploadPart>) {
        assertPartLocalSizeSanity(fileSize, actual)

        assertEquals(
            getRemoteFileSize(cipher, fileSize, encryptedChunkSize),
            actual.fold(0L) { i, p -> i + p.remoteSize },
            "Total part remoteSize should match remote data size"
        )
    }

    @Test
    fun `calcUploadParts should calculate the proper size for multiple parts`() {
        val expected = listOf(
            UploadPart(n=1, offset=0, localSize=5242880, remoteSize=5244000, isComplete=false),
            UploadPart(n=2, offset=5242880, localSize=5242880, remoteSize=5244000, isComplete=false),
            UploadPart(n=3, offset=10485760, localSize=2097152, remoteSize=2097600, isComplete=false)
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
    fun `calcUploadParts should calc the proper amount of chunks with no even chunks but with a last chunk`() {
        val fileSize = 10L.mb + 10
        val minPartSize = 5.mb
        val encryptedChunkSize = 128.kb
        val actual = calcUploadParts(cipher, fileSize, encryptedChunkSize, minPartSize)
        assertPartSanity(fileSize, encryptedChunkSize, actual)

        assertThat(actual).apply {
            describedAs("Should contain 3 parts")
            hasSize(3)
        }
    }

    @Test
    fun `calcUploadParts should calc the proper size when given a size smaller than the chunk size`() {
        val encryptedChunkSize = 128.kb
        val fileSize = 100L
        val actual = calcUploadParts(cipher, fileSize, encryptedChunkSize, 5.mb)

        assertThat(actual).apply {
            describedAs("Should only contain a single part")
            hasSize(1)
        }

        assertPartSanity(fileSize, encryptedChunkSize, actual)
    }

    @Test
    fun `calcUploadPartsEncrypted should calc parts when give a size smaller than the min part size`() {
        val fileSize = 20L
        val parts = calcUploadPartsEncrypted(fileSize, 5.mb)

        assertPartLocalSizeSanity(fileSize, parts)

        assertThat(parts).apply {
            describedAs("Should contain the proper part info")
            contains(UploadPart(1, 0, fileSize, fileSize, false))
        }
    }

    @Test
    fun `calcUploadPartsEncrypted should calc parts when given a size divisible by the min part size`() {
        val fileSize = 10L.mb
        val parts = calcUploadPartsEncrypted(fileSize, 5.mb)

        assertPartLocalSizeSanity(fileSize, parts)

        assertThat(parts).apply {
            describedAs("Should contain the proper part info")
            hasSize(2)
        }
    }

    @Test
    fun `calcUploadPartsEncrypted should calc parts when given a size with more than the min part size`() {
        val minPartSize = 5.mb
        val fileSize = minPartSize + 10L
        val parts = calcUploadPartsEncrypted(fileSize, minPartSize)

        assertPartLocalSizeSanity(fileSize, parts)

        assertThat(parts).apply {
            describedAs("Should contain the proper part info")
            hasSize(2)
        }
    }

    @Test
    fun `authFailureRetry should propagate non-auth exceptions`() {
        val authTokenManager = MockAuthTokenManager()

        val testSubscriber = authFailureRetry(authTokenManager, observable<Unit> {
            throw TestException()
        }).testSubscriber()

        testSubscriber.assertError(TestException::class.java)
    }

    @Test
    fun `authFailureRetry should retry when an auth exception occurs`() {
        val authTokenManager = MockAuthTokenManager()

        val o = object {
            private var n = 0

            fun run() {
                n += 1
                if (n == 1)
                    throw UnauthorizedException()
            }
        }

        val testSubscriber = authFailureRetry(authTokenManager, observable<Unit> {
            o.run()
        }, scheduler).testSubscriber()

        //this is messy...
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

        testSubscriber.assertCompleted()
    }
}