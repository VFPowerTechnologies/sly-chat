package io.slychat.messenger.core

import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals

class MD5InputStreamTest {
    @Test
    fun `it should return a valid hash string after reading data`() {
        val underlying = ByteArrayInputStream("test".toByteArray(Charsets.UTF_8))

        val inputStream = MD5InputStream(underlying)

        inputStream.readBytes()

        assertEquals("098f6bcd4621d373cade4e832627b4f6", inputStream.digestString, "Invalid checksum")
    }
}