package com.vfpowertech.keytap.core.http.api.offline

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.UserId

data class SerializedOfflineMessage(
    @JsonProperty("from")
    val from: UserId,
    @JsonProperty("timestamp")
    val timestamp: Int,
    @JsonProperty("message")
    val serializedMessage: String
)