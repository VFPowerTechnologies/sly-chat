package io.slychat.messenger.core.http.api.authentication

import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : AuthenticationAsyncClient {
    private fun newClient() = AuthenticationClient(serverUrl, factory.create())

    override fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception> = task {
        newClient().getParams(username)
    }

    override fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        newClient().auth(request)
    }
}