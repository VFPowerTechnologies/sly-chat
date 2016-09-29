package io.slychat.messenger.core.crypto.hashes

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/** Represents params for a hash function. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(BCryptParams::class, name = "bcrypt"),
    JsonSubTypes.Type(SCryptParams::class, name = "scrypt")
)
interface HashParams {
    /** Algorithm names must be in lowercase. */
    @get:JsonIgnore
    val algorithmName: String
}
