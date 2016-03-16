package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

/** Takes a list of email hashes to remove. */
data class RemoveContactsRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val contacts: List<String>
)