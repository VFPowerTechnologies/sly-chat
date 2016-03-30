package com.vfpowertech.keytap.core.http.api.offline

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.apiGetRequest
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class OfflineMessagesClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun get(request: OfflineMessagesGetRequest): OfflineMessagesGetResponse {
        val url = "$serverBaseUrl/v1/messages/get"
        val params = listOf("auth-token" to request.authToken)
        return apiGetRequest(httpClient, url, params, setOf(200), typeRef())
    }

    fun clear(request: OfflineMessagesClearRequest) {
        val url = "$serverBaseUrl/v1/messages/clear"
        apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef<ApiResult<EmptyResponse>>())
    }
}
