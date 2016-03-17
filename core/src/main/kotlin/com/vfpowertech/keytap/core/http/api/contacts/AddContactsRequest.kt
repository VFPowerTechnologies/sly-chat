package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class AddContactsRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val contacts: List<RemoteContactEntry>
)