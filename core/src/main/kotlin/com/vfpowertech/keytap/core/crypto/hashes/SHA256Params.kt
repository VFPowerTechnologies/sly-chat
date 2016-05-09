package com.vfpowertech.keytap.core.crypto.hashes

import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.unhexify

data class SHA256Params(
    val salt: ByteArray
) : HashParams {
    override val algorithmName: String = SHA256Params.algorithmName

    override fun serialize(): SerializedCryptoParams {
        return SerializedCryptoParams(algorithmName, mapOf("salt" to salt.hexify()))
    }

    companion object {
        val algorithmName: String = "sha-256"

        fun deserialize(params: Map<String, String>): HashParams {
            return SHA256Params(
                params["salt"]!!.unhexify()
            )
        }
    }
}