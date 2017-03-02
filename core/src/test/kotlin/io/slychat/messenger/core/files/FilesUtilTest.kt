package io.slychat.messenger.core.files

import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.randomFileMetadata
import io.slychat.messenger.core.randomUserMetadata
import org.junit.Test
import kotlin.test.assertEquals

class FilesUtilTest {
    @Test
    fun `decryptUserMetadata should decrypt result of encryptUserMetadata`() {
        val keyVault = generateNewKeyVault("test")
        val original = randomUserMetadata()

        val ciphertext = encryptUserMetadata(keyVault, original)
        val deserialized = decryptUserMetadata(keyVault, ciphertext)

        assertEquals(original, deserialized, "Invalid value")
    }

    @Test
    fun `decryptFileMetadata should decrypt result of encryptFileMetadata`() {
        val um = randomUserMetadata().copy(fileKey = generateKey(128))
        val original = randomFileMetadata()

        val ciphertext = encryptFileMetadata(um, original)
        val deserialized = decryptFileMetadata(um, ciphertext)

        assertEquals(original, deserialized, "Invalid value")
    }
}