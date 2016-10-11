package io.slychat.messenger.core.http.api.authentication

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : AuthenticationAsyncClient {
    private fun newClient() = AuthenticationClient(serverUrl, factory.create())

    override fun getParams(emailOrPhoneNumber: String): Promise<AuthenticationParamsResponse, Exception> = task {
        newClient().getParams(emailOrPhoneNumber)
    }

    override fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        newClient().auth(request)
    }

    override fun refreshToken(userCredentials: UserCredentials): Promise<AuthenticationRefreshResponse, Exception> = task {
        newClient().refreshToken(userCredentials)
    }
}