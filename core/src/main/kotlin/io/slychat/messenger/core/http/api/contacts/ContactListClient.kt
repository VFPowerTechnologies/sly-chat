package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.typeRef

class ContactListClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun addContacts(userCredentials: UserCredentials, request: AddContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/add"

        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }

    fun removeContacts(userCredentials: UserCredentials, request: RemoveContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list/remove"

        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }

    fun getContacts(userCredentials: UserCredentials): GetContactsResponse {
        val url = "$serverBaseUrl/v1/contact-list"

        return apiGetRequest(httpClient, url, userCredentials, listOf(), typeRef())
    }
}