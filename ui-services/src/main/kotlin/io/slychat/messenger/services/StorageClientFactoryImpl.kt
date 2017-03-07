package io.slychat.messenger.services

import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.storage.StorageClient
import io.slychat.messenger.core.http.api.storage.StorageClientImpl

class StorageClientFactoryImpl(
    private val serverBaseUrl: String,
    private val fileServerBaseUrl: String,
    private val httpClientFactory: HttpClientFactory
) : StorageClientFactory {
    override fun create(): StorageClient {
        return StorageClientImpl(serverBaseUrl, fileServerBaseUrl, httpClientFactory.create())
    }
}