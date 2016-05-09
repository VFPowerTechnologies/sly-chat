package com.vfpowertech.keytap.core.crypto

import com.vfpowertech.keytap.core.crypto.ciphers.AESGCMParams
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.hashes.BCryptParams
import com.vfpowertech.keytap.core.crypto.hashes.HashParams
import com.vfpowertech.keytap.core.crypto.hashes.SHA256Params
import java.util.*

interface Deserializer<T> {
    val algorithmName: String
    fun deserialize(params: Map<String, String>): T
}

abstract class Deserializers<T> {
    private var deserializers = HashMap<String, Deserializer<T>>()

    init {
        initSerializers()
    }

    fun register(deserializer: Deserializer<T>) {
        val algorithmName = deserializer.algorithmName
        
        if (algorithmName in deserializers)
            throw IllegalArgumentException("Deserializer for $algorithmName already registered")

        deserializers[algorithmName] = deserializer
    }

    fun deserialize(serializedParams: SerializedCryptoParams): T {
        val algorithmName = serializedParams.algorithmName
        val deserializer = deserializers[algorithmName]
        if (deserializer == null)
            throw IllegalArgumentException("Unsupported algorithm: $algorithmName")

        return deserializer.deserialize(serializedParams.params)
    }

    abstract fun initSerializers()
}

object HashDeserializers : Deserializers<HashParams>() {
    override fun initSerializers() {
        register(BCryptParams)
        register(SHA256Params)
    }
}

object CipherDeserializers : Deserializers<CipherParams>() {
    override fun initSerializers() {
        register(AESGCMParams)
    }
}
