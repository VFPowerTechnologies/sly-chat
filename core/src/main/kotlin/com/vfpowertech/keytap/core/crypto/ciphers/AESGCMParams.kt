package com.vfpowertech.keytap.core.crypto.ciphers

import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.unhexify
import com.vfpowertech.keytap.core.require

data class AESGCMParams(
    val iv: ByteArray,
    val authTagLength: Int
) : CipherParams {
    init {
        require(iv.isNotEmpty(), "iv must not be empty")
        require(authTagLength > 0, "authTagLength must be > 0")
    }

    override val algorithmName: String = "aes-gcm"

    override fun serialize(): Map<String, String> {
        return mapOf(
            "iv" to iv.hexify(),
            "authTagLength" to authTagLength.toString()
        )
    }

    companion object {
        fun deserialize(params: Map<String, String>): CipherParams {
            return AESGCMParams(
                params["iv"]!!.unhexify(),
                Integer.parseInt(params["authTagLength"]!!)
            )
        }
    }
}