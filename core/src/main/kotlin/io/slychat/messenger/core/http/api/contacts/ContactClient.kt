package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class ContactClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun find(userCredentials: UserCredentials, request: FindContactRequest): FindContactResponse {
        val url = "$serverBaseUrl/v1/contact/new/info"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun findAllById(userCredentials: UserCredentials, request: FindAllByIdRequest): FindAllByIdResponse {
        val url = "$serverBaseUrl/v1/contact/find"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun findById(userCredentials: UserCredentials, userId: UserId): FindByIdResponse {
        val url = "$serverBaseUrl/v1/contact/find/$userId"

        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef<ApiResult<FindByIdResponse>>())
    }

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): FindLocalContactsResponse {
        val url = "$serverBaseUrl/v1/contact/find-local"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }
}

