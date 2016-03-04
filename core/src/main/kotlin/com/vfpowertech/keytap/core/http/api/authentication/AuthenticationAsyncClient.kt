package com.vfpowertech.keytap.core.http.api.authentication

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationAsyncClient(serverUrl: String) {
    private val loginClient = AuthenticationClient(serverUrl, JavaHttpClient())

    fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception> = task {
        loginClient.getParams(username)
    }

    fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        loginClient.auth(request)
    }
}