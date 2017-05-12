package io.slychat.messenger.core.files

import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.randomFileMetadata
import io.slychat.messenger.core.randomUserMetadata
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesUtilTest {
    companion object {
        val keyVault = generateNewKeyVault("test")
    }

    @Test
    fun `decryptUserMetadata should decrypt result of encryptUserMetadata`() {
        val original = randomUserMetadata()

        val ciphertext = encryptUserMetadata(keyVault, original)
        val deserialized = decryptUserMetadata(keyVault, ciphertext)

        assertEquals(original, deserialized, "Invalid value")
    }

    @Test
    fun `decryptFileMetadata should decrypt result of encryptFileMetadata`() {
        val cipher = CipherList.defaultDataEncryptionCipher
        val um = randomUserMetadata().copy(fileKey = generateKey(cipher.keySizeBits))
        val original = randomFileMetadata()

        val ciphertext = encryptFileMetadata(um, original)
        val deserialized = decryptFileMetadata(um, ciphertext)

        assertEquals(original, deserialized, "Invalid value")
    }

    @Test
    fun `getFilePathHash should be case insensitive`() {
        val userMetadata1 = randomUserMetadata().copy(
            directory = "/Images",
            fileName = "Photo.jpg"
        )

        val userMetadata2 = userMetadata1.copy(
            directory = "/images",
            fileName = "photo.jpg"
        )

        assertTrue(getFilePathHash(keyVault, userMetadata1) == getFilePathHash(keyVault, userMetadata2), "Path hash not case insensitive")
    }

    //we don't do any special handling for cases in diff languages, so just use a uniform approach for now
    @Test
    fun `getFilePathHash should only lowercase ascii characters`() {
        val old = Locale.getDefault()
        Locale.setDefault(Locale("tr", "TR"))

        val r = try {
            val userMetadata1 = randomUserMetadata().copy(
                directory = "/Images",
                fileName = "I.jpg"
            )

            val userMetadata2 = userMetadata1.copy(
                directory = "/images",
                fileName = "i.jpg"
            )

            getFilePathHash(keyVault, userMetadata1) == getFilePathHash(keyVault, userMetadata2)
        }
        finally {
            Locale.setDefault(old)
        }

        assertTrue(r, "Path hash not case insensitive")
    }
}