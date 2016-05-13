package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty

/** Represents an API-level error. These are fatal errors, such as malformed requests. */
data class ApiError(
    @param:JsonProperty("message")
    @get:com.fasterxml.jackson.annotation.JsonProperty("message")
    val message: String
)