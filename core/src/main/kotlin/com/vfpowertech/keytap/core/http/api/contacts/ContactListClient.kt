package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.valueFromApi
import com.vfpowertech.keytap.core.typeRef

class ContactListClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun addContacts(request: AddContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/add"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        valueFromApi(resp, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }

    fun removeContacts(request: RemoveContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/remove"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        valueFromApi(resp, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }

    fun getContacts(request: GetContactsRequest): GetContactsResponse {
        val url = "$serverBaseUrl/v1/contact-list/get"
        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return valueFromApi(resp, setOf(200), typeRef<ApiResult<GetContactsResponse>>())
    }
}