package com.vfpowertech.keytap.core.crypto.ciphers

import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams
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

    override val algorithmName: String = AESGCMParams.algorithmName

    override val keyType: String = "AES"

    override fun serialize(): SerializedCryptoParams {
        return SerializedCryptoParams(algorithmName, mapOf(
            "iv" to iv.hexify(),
            "authTagLength" to authTagLength.toString()
        ))
    }

    companion object {
        val algorithmName: String = "aes-gcm"

        fun deserialize(params: Map<String, String>): CipherParams {
            return AESGCMParams(
                params["iv"]!!.unhexify(),
                Integer.parseInt(params["authTagLength"]!!)
            )
        }
    }
}