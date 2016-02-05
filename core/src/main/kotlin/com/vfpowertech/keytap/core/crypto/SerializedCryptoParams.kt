package com.vfpowertech.keytap.core.crypto

import com.fasterxml.jackson.annotation.JsonProperty

/** Serialized form of CipherParams and HashParams. */
data class SerializedCryptoParams(
    @JsonProperty("algorithmName") val algorithmName: String,
    @JsonProperty("params") val params: Map<String, String>
)