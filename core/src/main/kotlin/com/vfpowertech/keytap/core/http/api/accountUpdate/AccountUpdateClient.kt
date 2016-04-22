package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.typeRef

class AccountUpdateClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {

    fun updateName(request: UpdateNameRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/update/name"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }

    fun requestPhoneUpdate(request: RequestPhoneUpdateRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/request/phoneUpdate"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }

    fun confirmPhoneNumber(request: ConfirmPhoneNumberRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/update/phoneNumber"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }

    fun updateEmail(request: UpdateEmailRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/update/email"

        return apiPostRequest(httpClient, url, request, setOf(200, 400), typeRef())
    }
}
