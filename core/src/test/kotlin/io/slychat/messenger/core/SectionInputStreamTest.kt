package io.slychat.messenger.core

import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.assertEquals

class SectionInputStreamTest {
    private val s = "1234567890"
    private val max = s.length

    private fun <R> withTestStream(offset: Long, size: Long, body: (SectionInputStream) -> R): R {
        val f = File.createTempFile("sly", "")
        return try {
            val raf = RandomAccessFile(f, "rw")
            raf.use { raf ->
                raf.write(s.toByteArray())
                raf.seek(0)
                body(SectionInputStream(raf, offset, size))
            }
        }
        finally {
            f.delete()
        }
    }

    @Test
    fun `it should stop reading once the max length is reached`() {
        withTestStream(0, 2) {
            assertEquals("12", it.readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `it should seek to the given offset`() {
        withTestStream(2, 2) {
            assertEquals("34", it.readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `it should handle early EOF properly`() {
        withTestStream(max - 1L, 10) {
            assertEquals("0", it.readBytes().toString(Charsets.UTF_8))
        }
    }
}