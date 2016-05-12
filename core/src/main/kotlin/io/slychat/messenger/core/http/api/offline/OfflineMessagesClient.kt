package io.slychat.messenger.core.http.api.offline

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.typeRef

class OfflineMessagesClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun get(userCredentials: UserCredentials): OfflineMessagesGetResponse {
        val url = "$serverBaseUrl/v1/messages"
        return apiGetRequest(httpClient, url, userCredentials, listOf(), typeRef())
    }

    fun clear(userCredentials: UserCredentials, request: OfflineMessagesClearRequest) {
        val url = "$serverBaseUrl/v1/messages/clear"
        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }
}
