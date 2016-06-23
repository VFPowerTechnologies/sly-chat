package io.slychat.messenger.core.http.api.prekeys

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class HttpPreKeyClient(private val serverBaseUrl: String, private val httpClient: HttpClient) : PreKeyClient {
    override fun retrieve(userCredentials: UserCredentials, request: PreKeyRetrievalRequest): PreKeyRetrievalResponse {
        val params = mutableListOf(
            "user" to request.userId.long.toString()
        )

        if (request.deviceIds.isNotEmpty())
            params.add("devices" to request.deviceIds.joinToString(","))

        val url = "$serverBaseUrl/v1/prekeys"

        return apiGetRequest(httpClient, url, userCredentials, params, typeRef())
    }

    override fun store(userCredentials: UserCredentials, request: PreKeyStoreRequest): PreKeyStoreResponse {
        val url = "$serverBaseUrl/v1/prekeys"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    override fun getInfo(userCredentials: UserCredentials): PreKeyInfoResponse {
        val url = "$serverBaseUrl/v1/prekeys/info"

        return apiGetRequest(httpClient, url, userCredentials, listOf(), typeRef())
    }
}
