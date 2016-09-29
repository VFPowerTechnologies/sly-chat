package io.slychat.messenger.core.crypto.hashes

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(BCryptParams2::class, name = "bcrypt"),
    JsonSubTypes.Type(SCryptParams2::class, name = "scrypt")
)
interface HashParams2

class BCryptParams2(
    @JsonProperty("salt")
    val salt: ByteArray,
    @JsonProperty("cost")
    val cost: Int
) : HashParams2 {
    init {
        require(salt.isNotEmpty()) { "salt must not be empty" }
        require(cost >= 4 && cost <= 30) { "cost must be within the range [4, 30]" }
    }
}

class SCryptParams2(
    @JsonProperty("salt")
    val salt: ByteArray,
    @JsonProperty("N")
    val N: Int,
    @JsonProperty("r")
    val r: Int,
    @JsonProperty("p")
    val p: Int,
    @JsonProperty("keyLength")
    val keyLength: Int
) : HashParams2 {
    init {
        require(salt.isNotEmpty()) { "salt must not be empty" }
    }
}
