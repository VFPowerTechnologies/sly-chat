package com.vfpowertech.keytap.core.http.api.offline

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.apiGetRequest2
import com.vfpowertech.keytap.core.http.api.apiPostRequest2
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.typeRef

class OfflineMessagesClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun get(userCredentials: UserCredentials): OfflineMessagesGetResponse {
        val url = "$serverBaseUrl/v1/messages/get"
        return apiGetRequest2(httpClient, url, userCredentials, listOf(), setOf(200), typeRef())
    }

    fun clear(userCredentials: UserCredentials, request: OfflineMessagesClearRequest) {
        val url = "$serverBaseUrl/v1/messages/clear"
        apiPostRequest2(httpClient, url, userCredentials, request, setOf(200, 400), typeRef<ApiResult<EmptyResponse>>())
    }
}
