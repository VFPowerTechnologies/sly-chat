package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.UserCredentials
import com.vfpowertech.keytap.core.typeRef

class ContactClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun fetchContactInfo(userCredentials: UserCredentials, request: NewContactRequest): FetchContactResponse {
        val url = "$serverBaseUrl/v1/contact/new/info"

        return apiPostRequest(httpClient, url, userCredentials, request, setOf(200, 400), typeRef())
    }

    fun fetchContactInfoById(userCredentials: UserCredentials, request: FetchContactInfoByIdRequest): FetchContactInfoByIdResponse {
        val url = "$serverBaseUrl/v1/contact/find"

        return apiPostRequest(httpClient, url, userCredentials, request, setOf(200, 400), typeRef())
    }

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): FindLocalContactsResponse {
        val url = "$serverBaseUrl/v1/contact/find-local"

        return apiPostRequest(httpClient, url, userCredentials, request, setOf(200, 400), typeRef())
    }
}

