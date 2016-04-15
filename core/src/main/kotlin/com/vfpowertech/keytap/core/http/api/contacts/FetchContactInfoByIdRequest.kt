package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.UserId

data class FetchContactInfoByIdRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val ids: List<UserId>
)