package io.slychat.messenger.services.config

import io.slychat.messenger.core.crypto.EncryptionSpec
import io.slychat.messenger.core.crypto.decryptData
import io.slychat.messenger.core.crypto.encryptDataWithParams

interface ConfigCipher {
    fun encrypt(data: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

class EmptyConfigCipher : ConfigCipher {
    override fun encrypt(data: ByteArray): ByteArray = data
    override fun decrypt(data: ByteArray): ByteArray = data
}

class SymConfigCipher(private val encryptionSpec: EncryptionSpec) : ConfigCipher {
    override fun encrypt(data: ByteArray): ByteArray {
        return encryptDataWithParams(encryptionSpec, data).data
    }

    override fun decrypt(data: ByteArray): ByteArray {
        return decryptData(encryptionSpec, data)
    }

}
