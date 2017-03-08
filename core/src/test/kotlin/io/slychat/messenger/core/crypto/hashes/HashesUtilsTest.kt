package io.slychat.messenger.core.crypto.hashes

import io.slychat.messenger.core.crypto.getRandomBits
import org.junit.Test
import kotlin.test.assertEquals

class HashesUtilsTest {
    @Test
    fun `SCrypt should derive a key of the proper size`() {
        val keyLengthBits = 256
        val keyLengthBytes = keyLengthBits / 8

        val salt = getRandomBits(256)
        val N = 16384
        val r = 8
        val p = 1
        val params = HashParams.SCrypt2(salt, N, r, p, keyLengthBits)

        val key = hashPasswordWithParams("test", params, HashType.LOCAL)
        assertEquals(keyLengthBytes, key.size, "Invalid key size")
    }
}