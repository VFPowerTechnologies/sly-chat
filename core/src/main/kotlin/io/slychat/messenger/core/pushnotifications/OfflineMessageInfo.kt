package io.slychat.messenger.core.pushnotifications

import com.fasterxml.jackson.annotation.JsonProperty

data class OfflineMessageInfo(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("pendingCount")
    val pendingCount: Int
)