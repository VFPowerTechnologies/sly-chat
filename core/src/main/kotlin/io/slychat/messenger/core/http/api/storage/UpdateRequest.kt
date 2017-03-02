package io.slychat.messenger.core.http.api.storage

data class UpdateRequest(
    val delete: List<String>,
    val updateMetadata: Map<String, ByteArray>
)