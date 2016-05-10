package com.vfpowertech.keytap.core.http.api.gcm

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.apiPostRequest2
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.typeRef

class GcmClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(userCredentials: UserCredentials, request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/gcm/register"
        return apiPostRequest2(httpClient, url, userCredentials, request, setOf(200, 400, 403), typeRef())
    }

    fun unregister(userCredentials: UserCredentials, request: UnregisterRequest) {
        val url = "$serverBaseUrl/v1/gcm/unregister"
        apiPostRequest2(httpClient, url, userCredentials, request, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }
}
