package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.hashes.HashParams2

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountParams(
    @JsonProperty("sqlCipherName")
    val sqlCipherName: String,
    @JsonProperty("remoteHashParams")
    val remoteHashParams: HashParams2
)