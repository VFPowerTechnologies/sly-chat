package io.slychat.messenger.core.http

import io.slychat.messenger.core.crypto.tls.SSLConfigurator

/** Must be thread-safe. */
interface HttpClientFactory {
    fun create(): HttpClient
}

class JavaHttpClientFactory(
    private val config: HttpClientConfig,
    private val sslConfigurator: SSLConfigurator?
) : HttpClientFactory {
    override fun create(): HttpClient {
        return JavaHttpClient(config, sslConfigurator)
    }
}