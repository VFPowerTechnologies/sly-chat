package io.slychat.messenger.core.http.api.accountreset

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class ResetAccountClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun resetAccount(request: ResetAccountRequest): RequestResetAccountResponse {
        val url = "$serverBaseUrl/v1/account/reset/request"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }

    fun submitEmailResetCode(request: ResetConfirmCodeRequest): ResetAccountResponse {
        val url = "$serverBaseUrl/v1/account/reset/email/confirm"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }

    fun submitSmsResetCode(request: ResetConfirmCodeRequest): ResetAccountResponse {
        val url = "$serverBaseUrl/v1/account/reset/sms/confirm"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }
}
