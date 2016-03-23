package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class ContactListClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun addContacts(request: AddContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/add"

        apiPostRequest(httpClient, url, request, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }

    fun removeContacts(request: RemoveContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/remove"

        apiPostRequest(httpClient, url, request, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }

    fun getContacts(request: GetContactsRequest): GetContactsResponse {
        val url = "$serverBaseUrl/v1/contact-list/get"

        return apiPostRequest(httpClient, url, request, setOf(200), typeRef())
    }
}