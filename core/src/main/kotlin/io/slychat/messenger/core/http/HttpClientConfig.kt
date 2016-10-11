package io.slychat.messenger.core.http

data class HttpClientConfig(
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int
)
