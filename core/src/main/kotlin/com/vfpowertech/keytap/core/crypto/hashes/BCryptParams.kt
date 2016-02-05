package com.vfpowertech.keytap.core.crypto.hashes

import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams
import com.vfpowertech.keytap.core.require

/**
 * BCrypt hash params.
 *
 * Note that bcrypt is "special", in that its salt, and the resulting hash are in a special string format, as opposed to raw bytes.
 * Due to this, the cost value is actually never used as its embedded into the hash.
 *
 * @property salt
 * @property cost A value of [0, 30], as restricted by the java bcrypt library.
 *
 * @constructor
 */
data class BCryptParams(
    val salt: String,
    val cost: Int
) : HashParams {
    init {
        require(salt.isNotEmpty(), "salt must not be empty")
        require(cost >= 4 && cost <= 30, "cost must be within the range [4, 30]")
    }

    override val algorithmName: String = "bcrypt"

    override fun serialize(): SerializedCryptoParams {
        return SerializedCryptoParams(algorithmName, mapOf(
            "salt" to salt,
            "cost" to cost.toString()
        ))
    }

    companion object {
        fun deserialize(params: Map<String, String>): HashParams {
            return BCryptParams(
                params["salt"]!!,
                Integer.parseInt(params["cost"]!!)
            )
        }
    }
}