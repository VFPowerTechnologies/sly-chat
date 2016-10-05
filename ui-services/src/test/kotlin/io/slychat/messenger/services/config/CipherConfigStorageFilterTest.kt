package io.slychat.messenger.services.config

import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.getRandomBits
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CipherConfigStorageFilterTest {
    class MockPlaybackStorage : ConfigStorage {
        private var recorded: ByteArray? = null

        override fun write(data: ByteArray) {
            recorded = data
        }

        override fun read(): ByteArray? {
            return recorded ?: throw IllegalStateException("Write not called")
        }
    }

    @Test
    fun `it should be able to encrypt and decrypt data`() {
        val masterKey = getRandomBits(256)
        val spec = DerivedKeySpec(Key(masterKey), HKDFInfoList.accountLocalInfo())

        val storage = MockPlaybackStorage()
        val cipherStorage = CipherConfigStorageFilter(spec, storage)

        val original = "data"

        cipherStorage.write(original.toByteArray())
        val got = assertNotNull(cipherStorage.read(), "read() returned null")

        val final = String(got)

        assertEquals(original, final, "Decryption failed")
    }
}