package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.FileId

data class TextMessageAttachment(
    @JsonProperty("fileId")
    val fileId: FileId,
    @JsonProperty("shareKey")
    val shareKey: String,
    @JsonProperty("fileName")
    val fileName: String,
    @JsonProperty("fileKey")
    val fileKey: Key,
    @JsonProperty("cipherId")
    val cipherId: CipherId
)