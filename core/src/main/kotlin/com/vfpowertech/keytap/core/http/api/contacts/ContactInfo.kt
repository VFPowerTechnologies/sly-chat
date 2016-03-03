package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class ContactInfo(
    @param:JsonProperty("username")
    val username: String,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("phone_number")
    val phoneNumber: String,

    @param:JsonProperty("pub_key")
    val publicKey: String
)