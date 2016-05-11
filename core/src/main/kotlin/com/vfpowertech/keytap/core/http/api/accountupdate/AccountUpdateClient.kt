package com.vfpowertech.keytap.core.http.api.accountupdate

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.apiPostRequest
import com.vfpowertech.keytap.core.UserCredentials
import com.vfpowertech.keytap.core.typeRef

class AccountUpdateClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun updateName(userCredentials: UserCredentials, request: UpdateNameRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/update/name"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun requestPhoneUpdate(userCredentials: UserCredentials, request: RequestPhoneUpdateRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/request/phoneUpdate"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun confirmPhoneNumber(userCredentials: UserCredentials, request: ConfirmPhoneNumberRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/update/phoneNumber"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun updateEmail(userCredentials: UserCredentials, request: UpdateEmailRequest): AccountUpdateResponse {
        val url = "$serverBaseUrl/v1/account/update/email"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }
}
