package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class ContactListClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun getContacts(userCredentials: UserCredentials): GetContactsResponse {
        val url = "$serverBaseUrl/v1/contact-list"

        return apiGetRequest(httpClient, url, userCredentials, listOf(), typeRef())
    }

    fun updateContacts(userCredentials: UserCredentials, request: UpdateContactsRequest): Unit {
        val url = "$serverBaseUrl/v1/contact-list"

        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }
}
