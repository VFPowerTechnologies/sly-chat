package io.slychat.messenger.core.http

data class HttpClientConfig(
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long
)