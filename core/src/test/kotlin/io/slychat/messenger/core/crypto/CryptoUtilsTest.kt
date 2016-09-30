package io.slychat.messenger.core.crypto

import org.junit.Test
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
}
