package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteAuthTokenData(
    @JsonProperty("authToken")
    val authToken: String
)