package io.slychat.messenger.core.http.api.share

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ShareAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : ShareAsyncClient {
    private fun newClient(): ShareClientImpl = ShareClientImpl(serverUrl, factory.create())

    override fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): Promise<AcceptShareResponse, Exception> = task {
        newClient().acceptShare(userCredentials, request)
    }
}