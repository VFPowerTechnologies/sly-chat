package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.Key

data class TextMessageAttachment(
    @JsonProperty("fileId")
    val fileId: String,
    @JsonProperty("shareKey")
    val shareKey: String,
    @JsonProperty("fileName")
    val fileName: String,
    @JsonProperty("fileKey")
    val fileKey: Key
)