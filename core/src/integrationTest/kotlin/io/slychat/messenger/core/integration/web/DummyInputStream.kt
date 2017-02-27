package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.randomInt
import java.io.InputStream

/** Just returns random data until the given size is reached, then returns EOF. */
class DummyInputStream(private val size: Long) : InputStream() {
    private var readSoFar = 0

    override fun read(): Int {
        if (readSoFar >= size)
            return -1

        val r = randomInt(0, 255)
        readSoFar += 1
        return r
    }
}