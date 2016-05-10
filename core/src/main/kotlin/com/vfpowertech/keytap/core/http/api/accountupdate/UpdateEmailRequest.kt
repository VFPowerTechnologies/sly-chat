package com.vfpowertech.keytap.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateEmailRequest(
    @param:JsonProperty("email")
    @get:JsonProperty("email")
    val email: String
)