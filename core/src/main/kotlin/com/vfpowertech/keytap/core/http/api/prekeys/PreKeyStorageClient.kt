package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.ServerErrorException
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import com.vfpowertech.keytap.core.http.api.UnexpectedResponseException

class PreKeyStorageClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun store(request: PreKeyStoreRequest): PreKeyStoreResponse {
        val url = "$serverBaseUrl/store"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)

        return when (resp.responseCode) {
            200 -> PreKeyStoreResponse(true, null)
            400 -> try {
                objectMapper.readValue(resp.data, PreKeyStoreResponse::class.java)
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
