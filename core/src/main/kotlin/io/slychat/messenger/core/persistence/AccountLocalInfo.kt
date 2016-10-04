package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.persistence.sqlite.SQLCipherCipher

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountLocalInfo(
    @JsonProperty("sqlCipherCipher")
    val sqlCipherCipher: SQLCipherCipher,
    @JsonProperty("remoteHashParams")
    val remoteHashParams: HashParams
)