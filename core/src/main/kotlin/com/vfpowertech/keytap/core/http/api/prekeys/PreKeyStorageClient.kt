package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class PreKeyStorageClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun store(request: PreKeyStoreRequest): PreKeyStoreResponse {
        val url = "$serverBaseUrl/v1/store"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }
}
