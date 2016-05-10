package com.vfpowertech.keytap.core.crypto

import com.vfpowertech.keytap.core.crypto.hashes.BCryptParams
import org.junit.Test
import java.util.*
import kotlin.test.assertFalse

class CryptoUtilsTest {
    @Test
    fun `bcrypt should not have collisions with repeated passwords`() {
        val cost = 12
        val salt = getRandomBits(128)
        val params = BCryptParams(salt, cost)

        val hash1 = hashPasswordWithParams("test", params)
        val hash2 = hashPasswordWithParams("testtest", params)

        assertFalse(Arrays.equals(hash1, hash2), "Hashes match")
    }
}