package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.hashes.HashType
import io.slychat.messenger.core.crypto.hashes.hashPasswordWithParams
import org.junit.Test
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoUtilsTest {
    @Test
    fun `isValidUUIDFormat should return true for a valid UUID string`() {
        assertTrue(isValidUUIDFormat(randomUUID().toString()))
    }

    @Test
    fun `isValidUUIDFormat should return false for an empty string`() {
        assertFalse(isValidUUIDFormat(""))
    }

    @Test
    fun `isValidUUIDFormat should return false for an invalid length UUID string`() {
        assertFalse(isValidUUIDFormat(randomUUID().toString().substring(1)))
    }

    @Test
    fun `isValidUUIDFormat should return false for an invalid format UUID string`() {
        val invalid = "x" + randomUUID().toString().substring(1)
        assertFalse(isValidUUIDFormat(invalid))
    }

    @Test
    fun `hashPasswordWithParams should return different keys for the same IV and password`() {
        val password = "test"
        val params = defaultKeyPasswordHashParams()

        val remote = hashPasswordWithParams(password, params, HashType.REMOTE)
        val local = hashPasswordWithParams(password, params, HashType.LOCAL)

        assertFalse(Arrays.equals(remote, local), "Hashes should not be equal")
    }
}
