package com.vfpowertech.keytap.core.crypto.hashes

import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.unhexify

data class SHA256Params(
    val salt: ByteArray
) : HashParams {
    override val algorithmName: String = "sha-256"

    override fun serialize(): Map<String, String> {
        return mapOf("salt" to salt.hexify())
    }

    companion object {
        fun deserialize(params: Map<String, String>): HashParams {
            return SHA256Params(
                params["salt"]!!.unhexify()
            )
        }
    }
}