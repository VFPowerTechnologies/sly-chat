package com.vfpowertech.keytap.core.crypto

import java.util.*

abstract class Deserializers<T> {
    private var deserializers = HashMap<String, (Map<String, String>) -> T>()

    init {
        initSerializers()
    }

    fun registerDeserializer(algorithmName: String, deserializer: (Map<String, String>) -> T) {
        deserializers[algorithmName] = deserializer
    }

    fun fromMap(algorithmName: String, params: Map<String, String>): T {
        val deserializer = deserializers[algorithmName]
        if (deserializer == null)
            throw IllegalArgumentException("Unsupported algorithm: $algorithmName")

        return deserializer(params)
    }

    abstract fun initSerializers()
}

object HashDeserializers : Deserializers<HashParams>() {
    override fun initSerializers() {
        registerDeserializer("bcrypt", { BCryptParams.deserialize(it) })
    }
}

object CipherDeserializers : Deserializers<CipherParams>() {
    override fun initSerializers() {
        registerDeserializer("aes-gcm", { AESGCMParams.deserialize(it) })
    }
}
