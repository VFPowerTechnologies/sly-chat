package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiGetRequest
import com.vfpowertech.keytap.core.typeRef

class PreKeyRetrievalClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun retrieve(request: PreKeyRetrievalRequest): PreKeyRetrievalResponse {
        val params = listOf(
            "auth-token" to request.authToken,
            "username" to request.username
        )

        val url = "$serverBaseUrl/v1/retrieve"

        return apiGetRequest(httpClient, url, params, setOf(200, 400), typeRef())
    }
}