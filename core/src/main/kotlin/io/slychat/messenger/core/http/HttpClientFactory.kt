package io.slychat.messenger.core.http

/** Must be thread-safe. */
interface HttpClientFactory {
    fun create(): HttpClient
}

class JavaHttpClientFactory(private val config: HttpClientConfig) : HttpClientFactory {
    override fun create(): HttpClient {
        return JavaHttpClient()
    }
}