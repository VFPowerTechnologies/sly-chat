package io.slychat.messenger.core.crypto.hashes

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class BCryptParams(
    @JsonProperty("salt")
    val salt: ByteArray,
    @JsonProperty("cost")
    val cost: Int
) : HashParams {
    override val algorithmName: String
        get() = "bcrypt"

    init {
        require(salt.isNotEmpty()) { "salt must not be empty" }
        require(cost >= 4 && cost <= 30) { "cost must be within the range [4, 30]" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as BCryptParams

        if (!Arrays.equals(salt, other.salt)) return false
        if (cost != other.cost) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(salt)
        result = 31 * result + cost
        return result
    }

    override fun toString(): String {
        return "BCryptParams(cost=$cost)"
    }
}