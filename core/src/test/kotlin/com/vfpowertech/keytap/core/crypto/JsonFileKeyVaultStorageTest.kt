package com.vfpowertech.keytap.core.crypto

import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertTrue

class JsonFileKeyVaultStorageTest {
    @Test
    fun `should write and then read back a KeyVault`() {
        val password = "test"

        val keyVault = generateNewKeyVault(password)

        val path = File.createTempFile("keytap-test", ".json")
        path.deleteOnExit()
        val keyVaultStorage = JsonFileKeyVaultStorage(path)
        keyVault.toStorage(keyVaultStorage)

        val reloadedVault = KeyVault.fromStorage(keyVaultStorage, password)

        assertTrue(Arrays.equals(reloadedVault.identityKeyPair.serialize(), keyVault.identityKeyPair.serialize()))
    }
}