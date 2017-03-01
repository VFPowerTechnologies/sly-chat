package io.slychat.messenger.core.http

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpUtilsTest {
    @Test
    fun `toQueryString should return an empty string when given an empty params list`() {
        assertTrue(toQueryString(listOf()).isEmpty())
    }

    @Test
    fun `toQueryString should encode both its keys and values`() {
        assertEquals("k+k=v+v", toQueryString(listOf("k k" to "v v")))
    }

    @Test
    fun `toQueryString should not have a trailing &`() {
        assertEquals("k=v", toQueryString(listOf("k" to "v")))
    }

    @Test
    fun `toQueryString should encode unicode characters to UTF8`() {
        assertEquals("k=%e5%ad%94%e6%98%8e", toQueryString(listOf("k" to "孔明")).toLowerCase())
    }

    private fun testData2() {
        val boundary = "AKEUMkvzjKQKOBkE7Mql8dsa30P5F9y"

        val entities = listOf(
            MultipartPart.Text("fileId", "12345"),

            MultipartPart.Text("size", "49"),
            MultipartPart.Data("file", 49, ByteArrayInputStream(ByteArray(0)))
        )

        val totalSize = calcMultipartTotalSize(boundary, entities)

        assertEquals(387L, totalSize, "Invalid filesize for data")
    }

    private fun testData() {
        val boundary = "V401o-vuvaCf9aw6ngU6cDDuRoR2izn"

        val entities = listOf(
            MultipartPart.Text("fileId", "12345"),
            MultipartPart.Text("size", "241160"),
            MultipartPart.Data("file", 241160, ByteArrayInputStream(ByteArray(0)))
        )

        val totalSize = calcMultipartTotalSize(boundary, entities)

        assertEquals(241502L, totalSize, "Invalid filesize for data")
    }

    @Test
    fun `it should calc multipart entity sizes correctly`() {
        testData()
        testData2()
    }

    //XXX only checked during MultipartPart.Data; writeMultipartParts checks after each part
    @Test
    fun `writeMultipartPart should throw CancelledException if isCancelled is set to true`() {
        val isCancelled = AtomicBoolean(true)

        val outputStream = ByteArrayOutputStream()

        val bytes = byteArrayOf(0x77, 0x66)
        val part = MultipartPart.Data("data", bytes.size.toLong(), ByteArrayInputStream(bytes))
        assertFailsWith(CancellationException::class) {
            writeMultipartPart(outputStream, generateBoundary(), part, isCancelled)
        }
    }
}