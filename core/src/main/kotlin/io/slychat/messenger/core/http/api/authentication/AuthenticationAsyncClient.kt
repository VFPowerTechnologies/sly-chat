package io.slychat.messenger.core.http.api.authentication

import nl.komponents.kovenant.Promise

interface AuthenticationAsyncClient {
    fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception>

    fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception>
}
