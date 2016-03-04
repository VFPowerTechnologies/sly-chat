package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.valueFromApi
import com.vfpowertech.keytap.core.http.get
import com.vfpowertech.keytap.core.typeRef

class PreKeyRetrieveClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun retrieve(request: PreKeyRetrieveRequest): PreKeyRetrieveResponse {
        val params = listOf(
            "auth-token" to request.authToken,
            "username" to request.username
        )

        val url = "$serverBaseUrl/v1/retrieve"

        val resp = httpClient.get(url, params)
        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<PreKeyRetrieveResponse>>())
    }
}