package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.valueFromApi
import com.vfpowertech.keytap.core.http.get
import com.vfpowertech.keytap.core.typeRef

class AuthenticationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val url = "$serverBaseUrl/v1/login"
    private val objectMapper = ObjectMapper()

    fun getParams(username: String): AuthenticationParamsResponse {
        val resp = httpClient.get(url, listOf("username" to username))

        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<AuthenticationParamsResponse>>())
    }

    fun auth(authRequest: AuthenticationRequest): AuthenticationResponse {
        val json = objectMapper.writeValueAsBytes(authRequest)

        val resp = httpClient.postJSON(url, json)
        //auth failure -should- be using 401, but java's http client is retarded and refuses to return body content
        //when a 401 error is given
        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<AuthenticationResponse>>())
    }
}
