package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.ciphers.AES256CBCHMACCipher
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.testutils.withTempDir
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.io.InputStream
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AttachmentCacheImplTest {
    companion object {
        private val cipher = AES256CBCHMACCipher()

        private val fileKey = generateKey(cipher.keySizeBits)

        private val chunkSize = 10

        private val resolution = 20
    }

    private fun <R> withCache(body: (AttachmentCacheImpl) -> R): R {
        return withTempDir {
            val cache = AttachmentCacheImpl(it)
            cache.init()

            body(cache)
        }
    }

    private fun writeDummyOriginalFileTo(path: File): ByteArray {
        val dummyData = ByteArray(chunkSize) { it.toByte() }

        path.outputStream().use {
            it.write(cipher.encrypt(fileKey, dummyData))
        }

        return dummyData
    }

    //bit roundabout but w/e
    private fun <R> withDummyOriginalFull(body: (cache: AttachmentCacheImpl, fileId: String, original: ByteArray) -> R): R {
        return withCache {
            val fileId = generateFileId()
            val path = it.getPendingPathForFile(fileId)

            val data = writeDummyOriginalFileTo(path)

            it.markOriginalComplete(listOf(fileId)).get()

            body(it, fileId, data)
        }
    }

    private fun <R> withDummyOriginal(body: (cache: AttachmentCacheImpl, fileId: String) -> R): R {
        return withDummyOriginalFull { cache, fileId, _ -> body(cache, fileId) }
    }

    private fun <R> withDummyThumbnailFull(body: (cache: AttachmentCacheImpl, fileId: String, resolution: Int, original: ByteArray) -> R): R {
        return withDummyOriginalFull { cache, fileId, original ->
            cache.getThumbnailGenerationStreams(fileId, resolution, fileKey, cipher, chunkSize).use {
                val streams = assertNotNull(it, "No streams returned")
                streams.inputStream.copyTo(streams.outputStream)
            }
            cache.markThumbnailComplete(fileId, resolution).get()

            body(cache, fileId, resolution, original)
        }
    }

    private fun <R> withDummyThumbnail(body: (cache: AttachmentCacheImpl, fileId: String, resolution: Int) -> R): R {
        return withDummyThumbnailFull { cache, fileId, resolution, _ -> body(cache, fileId, resolution) }
    }

    private fun assertFileAvailable(inputStream: InputStream?) {
        assertNotNull(inputStream, "File should be available").close()
    }

    private fun assertFileUnavailable(inputStream: InputStream?) {
        try {
            assertNull(inputStream, "File should be unavailable")
        }
        catch (e: AssertionError) {
            inputStream?.close()
            throw e
        }
    }

    @Test
    fun `getOriginalImageInputStream should return null if image isn't present in active dir`() {
        withCache {
            val fileId = generateFileId()
            val path = it.getPendingPathForFile(fileId)

            writeDummyOriginalFileTo(path)

            assertFileUnavailable(it.getOriginalImageInputStream(fileId, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `getOriginalImageInputStream should return null if the image isn't present`() {
        withCache {
            val fileId = generateFileId()
            assertFileUnavailable(it.getOriginalImageInputStream(fileId, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `getThumbnailInputStream should return null if thumbnail isn't present in active dir`() {
        withDummyOriginal { cache, fileId ->
            cache.getThumbnailGenerationStreams(fileId, resolution, fileKey, cipher, chunkSize).use {
                val inputStream = cache.getThumbnailInputStream(fileId, resolution, fileKey, cipher, chunkSize)
                assertFileUnavailable(inputStream)
            }
        }
    }

    @Test
    fun `getThumbnailInputStream should return null if the thumbnail isn't present`() {
        withCache {
            val fileId = generateFileId()
            assertFileUnavailable(it.getThumbnailInputStream(fileId, resolution, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `getThumbnailInputStream should return null if the specified resolution is unavailable`() {
        withDummyThumbnail { cache, fileId, resolution ->
            assertFileUnavailable(cache.getThumbnailInputStream(fileId, resolution * 2, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `markOriginalComplete should move file to completed dir`() {
        withCache {
            val fileId = generateFileId()
            val path = it.getPendingPathForFile(fileId)

            writeDummyOriginalFileTo(path)

            it.markOriginalComplete(listOf(fileId)).get()

            assertFileAvailable(it.getOriginalImageInputStream(fileId, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `markThumbnailComplete should move thumbnail to completed dir`() {
        withDummyOriginal { cache, fileId ->
            cache.getThumbnailGenerationStreams(fileId, resolution, fileKey, cipher, chunkSize).use {
                val streams = assertNotNull(it, "No streams returned")
                streams.inputStream.copyTo(streams.outputStream)
            }

            cache.markThumbnailComplete(fileId, resolution).get()

            assertFileAvailable(cache.getThumbnailInputStream(fileId, resolution, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `a generated thumbnail should decrypt properly`() {
        withDummyThumbnailFull { cache, fileId, resolution, original ->
            val inputStream = assertNotNull(cache.getThumbnailInputStream(fileId, resolution, fileKey, cipher, chunkSize), "File should be available")

            assertThat(inputStream.use { it.readBytes() }).inHexadecimal().isEqualTo(original)
        }
    }

    @Test
    fun `delete should remove original file`() {
        withDummyOriginal { cache, fileId ->
            cache.delete(listOf(fileId)).get()

            assertFileUnavailable(cache.getOriginalImageInputStream(fileId, fileKey, cipher, chunkSize))
        }
    }

    //just move it after calling delete, and check if it exists to test this
    @Test
    fun `delete should remove pending original files`() {
        withCache {
            val fileId = generateFileId()
            val path = it.getPendingPathForFile(fileId)

            writeDummyOriginalFileTo(path)

            it.delete(listOf(fileId)).get()

            it.markOriginalComplete(listOf(fileId)).get()

            assertFileUnavailable(it.getOriginalImageInputStream(fileId, fileKey, cipher, chunkSize))
        }
    }


    @Test
    fun `delete should remove thumbnails`() {
        withDummyThumbnail { cache, fileId, resolution ->
            cache.delete(listOf(fileId)).get()

            assertFileUnavailable(cache.getThumbnailInputStream(fileId, resolution, fileKey, cipher, chunkSize))
        }
    }

    @Test
    fun `delete should remove pending thumbnails`() {
        withDummyOriginal { cache, fileId ->
            cache.getThumbnailGenerationStreams(fileId, resolution, fileKey, cipher, chunkSize).use {
                val streams = assertNotNull(it, "No streams returned")
                streams.inputStream.copyTo(streams.outputStream)
            }

            cache.delete(listOf(fileId)).get()

            cache.markThumbnailComplete(fileId, resolution).get()

            assertFileUnavailable(cache.getThumbnailInputStream(fileId, resolution, fileKey, cipher, chunkSize))
        }
    }
}