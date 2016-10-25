package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel

/**
 * Contact book details.
 */
data class UIContactInfo(
    @JsonProperty("id") val id: UserId,
    @JsonProperty("name") val name: String,
    @JsonProperty("email") val email: String,
    @JsonProperty("publicKey") val publicKey: String,
    @JsonProperty("allowedMessageLevel") val allowedMessageLevel: AllowedMessageLevel
)