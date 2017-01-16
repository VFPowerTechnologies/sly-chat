package io.slychat.messenger.core.http.api.pushnotifications

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class PushNotificationsClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun isRegistered(userCredentials: UserCredentials): IsRegisteredResponse {
        val url = "$serverBaseUrl/v1/push-notifications/registered"
        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef())
    }

    fun register(userCredentials: UserCredentials, request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/push-notifications/register"
        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun unregister(request: UnregisterRequest) {
        val url = "$serverBaseUrl/v1/push-notifications/unregister"
        apiPostRequest(httpClient, url, null, request, typeRef<ApiResult<EmptyResponse>>())
    }
}

