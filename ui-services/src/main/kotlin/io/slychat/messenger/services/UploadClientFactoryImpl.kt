package io.slychat.messenger.services

import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.http.api.upload.UploadClientImpl

class UploadClientFactoryImpl(
    private val serverBaseUrl: String,
    private val fileServerBaseUrl: String,
    private val httpClientFactory: HttpClientFactory
) : UploadClientFactory {
    override fun create(): UploadClient {
        return UploadClientImpl(serverBaseUrl, fileServerBaseUrl, httpClientFactory.create())
    }
}