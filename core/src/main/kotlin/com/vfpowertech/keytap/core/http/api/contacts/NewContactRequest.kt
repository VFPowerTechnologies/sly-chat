package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class NewContactRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("contact-username")
    @get:JsonProperty("contact-username")
    val username: String?,

    @param:JsonProperty("contact-phoneNumber")
    @get:JsonProperty("contact-phoneNumber")
    val phoneNumber: String?
)