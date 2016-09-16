package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.MessageInfo

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncSentMessageInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("conversationId")
    val conversationId: ConversationId,
    @JsonProperty("message")
    val message: String,
    @JsonProperty("timestamp")
    val timestamp: Long,
    @JsonProperty("receivedTimestamp")
    val receivedTimestamp: Long,
    @JsonProperty("ttl")
    val ttlMs: Long
) {
    fun toMessageInfo(): MessageInfo {
        return MessageInfo(
            id,
            message,
            timestamp,
            receivedTimestamp,
            true,
            true,
            true,
            false,
            ttlMs,
            0
        )
    }
}