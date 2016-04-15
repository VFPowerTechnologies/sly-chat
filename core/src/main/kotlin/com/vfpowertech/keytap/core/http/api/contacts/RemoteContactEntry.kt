package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class RemoteContactEntry(
    @JsonProperty("hash")
    val hash: String,
    @JsonProperty("encryptedUserId")
    val encryptedUserId: String
)