package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiGetRequest
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.UserCredentials
import com.vfpowertech.keytap.core.typeRef

class PreKeyClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun retrieve(userCredentials: UserCredentials, request: PreKeyRetrievalRequest): PreKeyRetrievalResponse {
        val params = mutableListOf(
            "user" to request.userId.long.toString()
        )

        if (request.deviceIds.isNotEmpty())
            params.add("devices" to request.deviceIds.joinToString(","))

        val url = "$serverBaseUrl/v1/prekeys"

        return apiGetRequest(httpClient, url, userCredentials, params, typeRef())
    }

    fun store(userCredentials: UserCredentials, request: PreKeyStoreRequest): PreKeyStoreResponse {
        val url = "$serverBaseUrl/v1/prekeys"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun getInfo(userCredentials: UserCredentials): PreKeyInfoResponse {
        val url = "$serverBaseUrl/v1/prekeys/info"

        return apiGetRequest(httpClient, url, userCredentials, listOf(), typeRef())
    }
}
