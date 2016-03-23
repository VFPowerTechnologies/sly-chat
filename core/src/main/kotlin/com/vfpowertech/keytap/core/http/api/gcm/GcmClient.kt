package com.vfpowertech.keytap.core.http.api.gcm

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.EmptyResponse
import com.vfpowertech.keytap.core.http.api.postRequest
import com.vfpowertech.keytap.core.typeRef

class GcmClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/gcm/register"
        return postRequest(httpClient, url, request, setOf(200, 400, 403), typeRef<ApiResult<RegisterResponse>>())
    }

    fun unregister(request: UnregisterRequest) {
        val url = "$serverBaseUrl/v1/gcm/unregister"
        postRequest(httpClient, url, request, setOf(200), typeRef<ApiResult<EmptyResponse>>())
    }
}
