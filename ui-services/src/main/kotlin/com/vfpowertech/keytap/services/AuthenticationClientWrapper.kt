package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationParamsResponse
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationResponse
import com.vfpowertech.keytap.services.ui.impl.getOrThrow
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationClientWrapper(serverUrl: String) {
    private val loginClient = AuthenticationClient(serverUrl, JavaHttpClient())

    fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception> = task {
        val apiResponse = loginClient.getParams(username)
        apiResponse.getOrThrow { it }
    }

    fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        val apiResponse = loginClient.auth(request)
        apiResponse.getOrThrow { it }
    }
}