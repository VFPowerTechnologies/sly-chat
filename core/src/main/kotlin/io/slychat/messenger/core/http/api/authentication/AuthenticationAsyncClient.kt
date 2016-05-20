package io.slychat.messenger.core.http.api.authentication

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationAsyncClient(private val serverUrl: String) {
    private fun newClient() = AuthenticationClient(serverUrl, io.slychat.messenger.core.http.JavaHttpClient())

    fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception> = task {
        newClient().getParams(username)
    }

    fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        newClient().auth(request)
    }
}