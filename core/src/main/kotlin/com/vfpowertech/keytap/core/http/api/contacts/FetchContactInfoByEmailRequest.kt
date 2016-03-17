package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class FetchContactInfoByEmailRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val emails: List<String>
)