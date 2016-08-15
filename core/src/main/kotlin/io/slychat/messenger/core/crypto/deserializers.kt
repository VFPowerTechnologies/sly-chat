package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.AESGCMParams
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.crypto.hashes.BCryptParams
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.crypto.hashes.SCryptParams
import io.slychat.messenger.core.crypto.hashes.SHA256Params
import java.util.*

interface Deserializer<out T> {
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
        val deserializer = deserializers[algorithmName] ?: throw IllegalArgumentException("Unsupported algorithm: $algorithmName")

        return deserializer.deserialize(serializedParams.params)
    }

    abstract fun initSerializers()
}

object HashDeserializers : Deserializers<HashParams>() {
    override fun initSerializers() {
        register(BCryptParams.Companion)
        register(SCryptParams.Companion)
        register(SHA256Params.Companion)
    }
}

object CipherDeserializers : Deserializers<CipherParams>() {
    override fun initSerializers() {
        register(AESGCMParams)
    }
}
