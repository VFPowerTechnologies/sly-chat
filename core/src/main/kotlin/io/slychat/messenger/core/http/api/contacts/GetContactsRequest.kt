package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class GetContactsRequest(@get:JsonProperty("auth-token") val authToken: String)