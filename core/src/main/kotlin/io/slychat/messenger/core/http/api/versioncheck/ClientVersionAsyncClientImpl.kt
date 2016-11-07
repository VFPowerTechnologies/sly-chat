package io.slychat.messenger.core.http.api.versioncheck

import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ClientVersionAsyncClientImpl(private val serverBaseUrl: String, private val factory: HttpClientFactory) : ClientVersionAsyncClient {
    private fun newClient() = ClientVersionClientImpl(serverBaseUrl, factory.create())

    override fun check(version: String): Promise<CheckResponse, Exception> = task {
        newClient().check(version)
    }
}