package io.slychat.messenger.core.crypto.hashes

import io.slychat.messenger.core.crypto.Deserializer
import io.slychat.messenger.core.crypto.SerializedCryptoParams
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.crypto.unhexify

class SCryptParams(
    val salt: ByteArray,
    val N: Int,
    val r: Int,
    val p: Int,
    val keyLength: Int
) : HashParams {
    init {
        require(salt.isNotEmpty()) { "salt must not be empty" }
        //BCrypt.generate checks all the param invariants for us
    }

    override val algorithmName: String = Companion.algorithmName

    override fun serialize(): SerializedCryptoParams {
        return SerializedCryptoParams(algorithmName, mapOf(
            "salt" to salt.hexify(),
            "N" to N.toString(),
            "r" to r.toString(),
            "p" to p.toString(),
            "keyLength" to keyLength.toString()
        ))
    }

    companion object : Deserializer<HashParams> {
        override val algorithmName: String = "scrypt"

        override fun deserialize(params: Map<String, String>): HashParams {
            return SCryptParams(
                params["salt"]!!.unhexify(),
                Integer.parseInt(params["N"]!!),
                Integer.parseInt(params["r"]!!),
                Integer.parseInt(params["p"]!!),
                Integer.parseInt(params["keyLength"]!!)
            )
        }
    }
}