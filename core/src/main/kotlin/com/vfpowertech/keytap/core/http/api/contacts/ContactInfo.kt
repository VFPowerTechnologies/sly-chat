package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class ContactInfo(
    @JsonProperty("username")
    val username: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("phone_number")
    val phoneNumber: String,

    @JsonProperty("pub_key")
    val publicKey: String
)