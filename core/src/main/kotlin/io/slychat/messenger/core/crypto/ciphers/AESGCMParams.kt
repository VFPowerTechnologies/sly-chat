package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.Deserializer
import io.slychat.messenger.core.crypto.SerializedCryptoParams
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.crypto.unhexify

class AESGCMParams(
    val iv: ByteArray,
    val authTagLength: Int
) : CipherParams {
    init {
        require(iv.isNotEmpty()) { "iv must not be empty" }
        require(authTagLength > 0) { "authTagLength must be > 0" }
    }

    override val algorithmName: String = Companion.algorithmName

    override val keyType: String = "AES"

    override fun serialize(): SerializedCryptoParams {
        return SerializedCryptoParams(algorithmName, mapOf(
            "iv" to iv.hexify(),
            "authTagLength" to authTagLength.toString()
        ))
    }

    companion object : Deserializer<CipherParams> {
        override val algorithmName: String = "aes-gcm"

        override fun deserialize(params: Map<String, String>): CipherParams {
            return AESGCMParams(
                params["iv"]!!.unhexify(),
                Integer.parseInt(params["authTagLength"]!!)
            )
        }
    }
}