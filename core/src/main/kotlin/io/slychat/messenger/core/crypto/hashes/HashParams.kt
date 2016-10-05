package io.slychat.messenger.core.crypto.hashes

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.*

/** Represents params for a hash function. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(HashParams.SCrypt::class, name = "scrypt")
)
sealed class HashParams {
    /** Algorithm names must be in lowercase. */
    @get:JsonIgnore
    abstract val algorithmName: String

    class SCrypt(
        @JsonProperty("salt")
        val salt: ByteArray,
        //getN -> n
        @JsonProperty("n")
        val n: Int,
        @JsonProperty("r")
        val r: Int,
        @JsonProperty("p")
        val p: Int,
        @JsonProperty("keyLengthBits")
        val keyLengthBits: Int
    ) : HashParams() {
        override val algorithmName: String
            get() = "scrypt"

        init {
            require(salt.isNotEmpty()) { "salt must not be empty" }
        }

        override fun toString(): String {
            return "SCrypt(n=$n, r=$r, p=$p, keyLength=$keyLengthBits)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as SCrypt

            if (!Arrays.equals(salt, other.salt)) return false
            if (n != other.n) return false
            if (r != other.r) return false
            if (p != other.p) return false
            if (keyLengthBits != other.keyLengthBits) return false

            return true
        }

        override fun hashCode(): Int {
            var result = Arrays.hashCode(salt)
            result = 31 * result + n
            result = 31 * result + r
            result = 31 * result + p
            result = 31 * result + keyLengthBits
            return result
        }
    }
}
