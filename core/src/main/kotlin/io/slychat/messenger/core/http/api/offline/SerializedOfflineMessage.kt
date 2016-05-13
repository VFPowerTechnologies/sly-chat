package io.slychat.messenger.core.http.api.offline

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.SlyAddress

data class SerializedOfflineMessage(
    @JsonProperty("from")
    val from: SlyAddress,
    @JsonProperty("timestamp")
    val timestamp: Int,
    @JsonProperty("message")
    val serializedMessage: String
)