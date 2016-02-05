package com.vfpowertech.keytap.core.crypto

import com.vfpowertech.keytap.core.crypto.ciphers.AESGCMParams
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.hashes.BCryptParams
import com.vfpowertech.keytap.core.crypto.hashes.HashParams
import com.vfpowertech.keytap.core.crypto.hashes.SHA256Params
import java.util.*

abstract class Deserializers<T> {
    private var deserializers = HashMap<String, (Map<String, String>) -> T>()

    init {
        initSerializers()
    }

    fun registerDeserializer(algorithmName: String, deserializer: (Map<String, String>) -> T) {
        deserializers[algorithmName] = deserializer
    }

    fun deserialize(serializedParams: SerializedCryptoParams): T {
        val algorithmName = serializedParams.algorithmName
        val deserializer = deserializers[algorithmName]
        if (deserializer == null)
            throw IllegalArgumentException("Unsupported algorithm: $algorithmName")

        return deserializer(serializedParams.params)
    }

    abstract fun initSerializers()
}

object HashDeserializers : Deserializers<HashParams>() {
    override fun initSerializers() {
        registerDeserializer("bcrypt", { BCryptParams.deserialize(it) })
        registerDeserializer("sha-256", { SHA256Params.deserialize(it) })
    }
}

object CipherDeserializers : Deserializers<CipherParams>() {
    override fun initSerializers() {
        registerDeserializer("aes-gcm", { AESGCMParams.deserialize(it) })
    }
}
