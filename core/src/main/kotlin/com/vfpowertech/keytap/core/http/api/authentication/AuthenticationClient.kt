package com.vfpowertech.keytap.core.http.api.authentication

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiGetRequest
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class AuthenticationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val url = "$serverBaseUrl/v1/login"

    fun getParams(username: String): AuthenticationParamsResponse {
        val params = listOf("username" to username)

        return apiGetRequest(httpClient, url, params, setOf(200, 400), typeRef())
    }

    fun auth(request: AuthenticationRequest): AuthenticationResponse {
        //auth failure -should- be using 401, but java's http client is retarded and refuses to return body content
        //when a 401 error is given
        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }
}
