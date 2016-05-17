package io.slychat.messenger.services

import com.fasterxml.jackson.annotation.JsonProperty

/** Simple text message to a single user. */
data class SingleUserTextMessage(
    @JsonProperty("timestamp")
    val timestamp: Long,
    @JsonProperty("message")
    val message: String
)