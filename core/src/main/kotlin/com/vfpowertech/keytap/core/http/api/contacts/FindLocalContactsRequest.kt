package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.PlatformContact

data class FindLocalContactsRequest(
    @param:JsonProperty("auth-token")
    @get:com.fasterxml.jackson.annotation.JsonProperty("auth-token")
    val authToken: String,

    @JsonProperty("contacts")
    val contacts: List<PlatformContact>
)