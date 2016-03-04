package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationParamsResponse
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationResponse
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationClientWrapper(serverUrl: String) {
    private val loginClient = AuthenticationClient(serverUrl, JavaHttpClient())

    fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception> = task {
        loginClient.getParams(username)
    }

    fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        loginClient.auth(request)
    }
}