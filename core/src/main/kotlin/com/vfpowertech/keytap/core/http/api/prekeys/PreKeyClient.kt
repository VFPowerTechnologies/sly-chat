package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiGetRequest
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class PreKeyClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun retrieve(request: PreKeyRetrievalRequest): PreKeyRetrievalResponse {
        val params = mutableListOf(
            "auth-token" to request.authToken,
            "user" to request.userId.id.toString()
        )

        if (request.deviceIds.isNotEmpty())
            params.add("devices" to request.deviceIds.joinToString(","))

        val url = "$serverBaseUrl/v1/prekeys"

        return apiGetRequest(httpClient, url, params, setOf(200, 400), typeRef())
    }

    fun store(request: PreKeyStoreRequest): PreKeyStoreResponse {
        val url = "$serverBaseUrl/v1/prekeys"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }

    fun getInfo(request: PreKeyInfoRequest): PreKeyInfoResponse {
        val params = listOf(
            "auth-token" to request.authToken
        )

        val url = "$serverBaseUrl/v1/prekeys/info"

        return apiGetRequest(httpClient, url, params, setOf(200, 400), typeRef())
    }
}
