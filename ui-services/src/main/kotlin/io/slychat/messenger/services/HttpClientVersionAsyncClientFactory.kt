package io.slychat.messenger.services

import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.versioncheck.ClientVersionAsyncClient
import io.slychat.messenger.core.http.api.versioncheck.ClientVersionAsyncClientImpl

class HttpClientVersionAsyncClientFactory(
    private val serverUrlBase: String,
    private val httpClientFactory: HttpClientFactory
) : ClientVersionAsyncClientFactory {

    override fun create(): ClientVersionAsyncClient {
        return ClientVersionAsyncClientImpl(serverUrlBase, httpClientFactory)
    }
}