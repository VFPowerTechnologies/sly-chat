package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.Config
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationParamsResponse
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationResponse
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AuthenticationClientWrapper() {
    private val loginClient = AuthenticationClient(Config.AUTH_SERVER, JavaHttpClient())

    fun getParams(username: String): Promise<AuthenticationParamsResponse, Exception> = task {
        val apiResponse = loginClient.getParams(username)
        apiResponse.getOrThrow { it }
    }

    fun auth(request: AuthenticationRequest): Promise<AuthenticationResponse, Exception> = task {
        val apiResponse = loginClient.auth(request)
        apiResponse.getOrThrow { it }
    }
}