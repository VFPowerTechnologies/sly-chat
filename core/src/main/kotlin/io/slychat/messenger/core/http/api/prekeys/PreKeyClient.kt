package io.slychat.messenger.core.http.api.prekeys

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.typeRef

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
