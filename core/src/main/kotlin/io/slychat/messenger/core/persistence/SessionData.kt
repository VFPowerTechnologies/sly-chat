package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.AuthToken

@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionData(
    @param:JsonProperty("authToken")
    val authToken: AuthToken?,
    @param:JsonProperty("relayClockDifference")
    val relayClockDifference: Long
) {
    constructor() : this(null, 0)

    override fun toString(): String {
        return "SessionData(authToken=..., relayClockDifference=$relayClockDifference)"
    }
}