package io.slychat.messenger.core.http.api.authentication

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class AuthenticationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val url = "$serverBaseUrl/v1/login"

    fun getParams(username: String): AuthenticationParamsResponse {
        val params = listOf("username" to username)

        return apiGetRequest(httpClient, url, null, params, typeRef())
    }

    fun auth(request: AuthenticationRequest): AuthenticationResponse {
        //auth failure -should- be using 401, but java's http client is retarded and refuses to return body content
        //when a 401 error is given
        return apiPostRequest(httpClient, url, null, request, typeRef())
    }
}
