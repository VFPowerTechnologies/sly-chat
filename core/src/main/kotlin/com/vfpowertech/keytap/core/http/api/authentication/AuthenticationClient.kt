package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.throwApiException
import com.vfpowertech.keytap.core.http.get
import com.vfpowertech.keytap.core.typeRef

class AuthenticationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val url = "$serverBaseUrl/v1/login"
    private val objectMapper = ObjectMapper()

    fun getParams(username: String): ApiResult<AuthenticationParamsResponse> {
        val resp = httpClient.get(url, listOf("username" to username))

        return when (resp.code) {
            200, 400 -> try {
                objectMapper.readValue<ApiResult<AuthenticationParamsResponse>>(resp.body, typeRef<ApiResult<AuthenticationParamsResponse>>())
            }
            catch (e: JsonProcessingException) {
                throw InvalidResponseBodyException(resp, e)
            }
            else -> throwApiException(resp)
        }
    }

    fun auth(authRequest: AuthenticationRequest): ApiResult<AuthenticationResponse> {
        val json = objectMapper.writeValueAsBytes(authRequest)

        val resp = httpClient.postJSON(url, json)
        return when (resp.code) {
            //auth failure -should- be using 401, but java's http client is retarded and refuses to return body content
            //when a 401 error is given
            200, 400, 401 -> try {
                objectMapper.readValue<ApiResult<AuthenticationResponse>>(resp.body, typeRef<ApiResult<AuthenticationResponse>>())
            }
            catch (e: JsonProcessingException) {
                throw InvalidResponseBodyException(resp, e)
            }
            else -> throwApiException(resp)
        }
    }
}
