package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.throwApiException
import com.vfpowertech.keytap.core.typeRef

class ContactClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun fetchContactInfo(request: NewContactRequest): ApiResult<FetchContactResponse> {
        val url = "$serverBaseUrl/v1/contact/new/info"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return when (resp.code) {
            200, 400 -> try {
                objectMapper.readValue<ApiResult<FetchContactResponse>>(resp.body, typeRef<ApiResult<FetchContactResponse>>())
            }
            catch (e: JsonProcessingException) {
                throw InvalidResponseBodyException(resp, e)
            }
            else -> throwApiException(resp)
        }
    }
}