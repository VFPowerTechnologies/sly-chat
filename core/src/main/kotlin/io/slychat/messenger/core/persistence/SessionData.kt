package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.AuthToken

@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionData(
    @param:JsonProperty("authToken")
    val authToken: AuthToken?
) {
    constructor() : this(null)
}