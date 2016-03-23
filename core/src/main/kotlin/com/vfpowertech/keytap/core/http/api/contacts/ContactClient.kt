package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class ContactClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun fetchContactInfo(request: NewContactRequest): FetchContactResponse {
        val url = "$serverBaseUrl/v1/contact/new/info"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }

    fun fetchContactInfoByEmail(request: FetchContactInfoByEmailRequest): FetchContactInfoByEmailResponse {
        val url = "$serverBaseUrl/v1/contact/find"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }

    fun findLocalContacts(request: FindLocalContactsRequest): FindLocalContactsResponse {
        val url = "$serverBaseUrl/v1/contact/find-local"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }
}

