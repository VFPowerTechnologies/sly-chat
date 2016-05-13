package io.slychat.messenger.core.crypto

import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonFileKeyVaultStorageTest {
    val password = "test"
    lateinit var keyVault: KeyVault
    lateinit var keyVaultStorage: JsonFileKeyVaultStorage

    @Before
    fun before() {
        keyVault = generateNewKeyVault(password)
        val path = getTempKeyVaultPath()
        keyVaultStorage = JsonFileKeyVaultStorage(path)
    }

    fun getTempKeyVaultPath(): File {
        val path = File.createTempFile("keytap-test", ".json")
        path.deleteOnExit()
        return path
    }

    @Test
    fun `should write and then read back a KeyVault`() {
        keyVault.toStorage(keyVaultStorage)

        val reloadedVault = KeyVault.fromStorage(keyVaultStorage, password)

        assertTrue(Arrays.equals(reloadedVault.identityKeyPair.serialize(), keyVault.identityKeyPair.serialize()))
    }

    @Test
    fun `should throw KeyVaultDecryptionFailedException if given an invalid password`() {
        keyVault.toStorage(keyVaultStorage)

        assertFailsWith(KeyVaultDecryptionFailedException::class) {
            KeyVault.fromStorage(keyVaultStorage, "")
        }
    }
}