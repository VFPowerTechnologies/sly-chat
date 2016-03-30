package com.vfpowertech.keytap.core.http.api.offline

import com.fasterxml.jackson.annotation.JsonProperty

data class SerializedOfflineMessage(
    @JsonProperty("from")
    val from: String,
    @JsonProperty("timestamp")
    val timestamp: Int,
    @JsonProperty("message")
    val serializedMessage: String
)