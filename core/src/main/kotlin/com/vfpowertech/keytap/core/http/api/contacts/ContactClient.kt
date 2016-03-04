package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.valueFromApi
import com.vfpowertech.keytap.core.typeRef

class ContactClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun fetchContactInfo(request: NewContactRequest): FetchContactResponse {
        val url = "$serverBaseUrl/v1/contact/new/info"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<FetchContactResponse>>())
    }
}