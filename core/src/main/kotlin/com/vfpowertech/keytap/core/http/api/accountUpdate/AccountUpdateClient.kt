package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class AccountUpdateClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {

    fun updatePhone(request: UpdatePhoneRequest): UpdatePhoneResponse {
        val url = "$serverBaseUrl/v1/account/update/phone"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }
}
