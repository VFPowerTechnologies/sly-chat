package io.slychat.messenger.core.crypto.hashes

import io.slychat.messenger.core.crypto.Deserializer
import io.slychat.messenger.core.crypto.SerializedCryptoParams
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.unhexify

class SHA256Params(
    val salt: ByteArray
) : HashParams {
    override val algorithmName: String = Companion.algorithmName

    override fun serialize(): SerializedCryptoParams {
        return SerializedCryptoParams(algorithmName, mapOf("salt" to salt.hexify()))
    }

    companion object : Deserializer<HashParams> {
        override val algorithmName: String = "sha256"

        override fun deserialize(params: Map<String, String>): HashParams {
            return SHA256Params(
                params["salt"]!!.unhexify()
            )
        }
    }
}