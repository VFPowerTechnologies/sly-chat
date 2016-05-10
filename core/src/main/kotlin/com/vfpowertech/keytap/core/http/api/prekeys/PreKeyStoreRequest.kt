package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

/** All stores are in stored as hexified strings. */
data class PreKeyStoreRequest(
    @JsonProperty("registrationId")
    val registrationId: Int,

    @JsonProperty("identityKey")
    val identityKey: String,

    @JsonProperty("bundle")
    val bundle: SerializedPreKeyBundle
)