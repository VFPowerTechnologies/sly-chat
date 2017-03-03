package io.slychat.messenger.core.http.api.storage

class MetadataUpdateRequest(
    val pathHash: String,
    val metadata: ByteArray
)