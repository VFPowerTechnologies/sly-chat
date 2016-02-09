package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.ServerErrorException
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import com.vfpowertech.keytap.core.http.api.UnexpectedResponseException

class PreKeyRetrieveClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun retrieve(request: PreKeyRetrieveRequest): PreKeyRetrieveResponse {
        val objectMapper = ObjectMapper()

        val params = listOf(
            "auth-token" to request.authToken,
            "username" to request.username
        )

        val url = "$serverBaseUrl/retrieve"

        val resp = httpClient.get(url, params)

        return when (resp.responseCode) {
            200 -> try {
                objectMapper.readValue(resp.data, PreKeyRetrieveResponse::class.java)
            }
            catch (e: JsonProcessingException) {
                throw InvalidResponseBodyException(resp, e)
            }
            401 -> throw UnauthorizedException(resp)
            in 500..599 -> throw ServerErrorException(resp)
            else -> throw UnexpectedResponseException(resp)
        }
    }
}