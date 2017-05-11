package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty

data class MessageAttachmentInfo(
    @JsonProperty("n")
    val n: Int,

    //name as given by the sender
    @JsonProperty("displayName")
    val displayName: String,

    //may be invalid if file was deleted since
    @JsonProperty("fileId")
    val fileId: String,

    @JsonProperty("isInline")
    //whether or not to request from cache
    val isInline: Boolean
)