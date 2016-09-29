package io.slychat.messenger.core.crypto.hashes

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class SCryptParams(
    @JsonProperty("salt")
    val salt: ByteArray,
    //getN -> n
    @JsonProperty("n")
    val n: Int,
    @JsonProperty("r")
    val r: Int,
    @JsonProperty("p")
    val p: Int,
    @JsonProperty("keyLength")
    val keyLength: Int
) : HashParams {
    override val algorithmName: String
        get() = "scrypt"

    init {
        require(salt.isNotEmpty()) { "salt must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as SCryptParams

        if (!Arrays.equals(salt, other.salt)) return false
        if (n != other.n) return false
        if (r != other.r) return false
        if (p != other.p) return false
        if (keyLength != other.keyLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(salt)
        result = 31 * result + n
        result = 31 * result + r
        result = 31 * result + p
        result = 31 * result + keyLength
        return result
    }

    override fun toString(): String {
        return "SCryptParams(n=$n, r=$r, p=$p, keyLength=$keyLength)"
    }
}