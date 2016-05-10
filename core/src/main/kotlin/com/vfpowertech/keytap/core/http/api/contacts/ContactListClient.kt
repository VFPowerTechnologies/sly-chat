package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.apiGetRequest2
import com.vfpowertech.keytap.core.http.api.apiPostRequest2
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.typeRef

class ContactListClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun addContacts(userCredentials: UserCredentials, request: AddContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/add"

        apiPostRequest2(httpClient, url, userCredentials, request, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }

    fun removeContacts(userCredentials: UserCredentials, request: RemoveContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/remove"

        apiPostRequest2(httpClient, url, userCredentials, request, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }

    fun getContacts(userCredentials: UserCredentials): GetContactsResponse {
        val url = "$serverBaseUrl/v1/contact-list"

        return apiGetRequest2(httpClient, url, userCredentials, listOf(), setOf(200), typeRef())
    }
}