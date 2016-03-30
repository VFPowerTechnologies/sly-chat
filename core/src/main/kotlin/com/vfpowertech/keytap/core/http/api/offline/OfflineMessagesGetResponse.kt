package com.vfpowertech.keytap.core.http.api.offline

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @property range Treat as opaque. Used to id this bundle of offline messages for deletion.
 * @property messages List of messages. Treat as unordered.
 */
data class OfflineMessagesGetResponse(
    @JsonProperty("range")
    val range: String,
    @JsonProperty("messages")
    val messages: List<SerializedOfflineMessage>
)
