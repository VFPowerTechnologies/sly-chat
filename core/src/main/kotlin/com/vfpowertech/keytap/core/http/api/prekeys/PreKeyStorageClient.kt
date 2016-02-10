package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.throwApiException
import com.vfpowertech.keytap.core.typeRef

class PreKeyStorageClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun store(request: PreKeyStoreRequest): ApiResult<PreKeyStoreResponse> {
        val url = "$serverBaseUrl/store"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)

        return when (resp.code) {
            200, 400 -> try {
                objectMapper.readValue<ApiResult<PreKeyStoreResponse>>(resp.body, typeRef<ApiResult<PreKeyStoreResponse>>())
            }
            catch (e: JsonProcessingException) {
                throw InvalidResponseBodyException(resp, e)
            }
            else -> throwApiException(resp)
        }
    }
}
