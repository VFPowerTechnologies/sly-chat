package io.slychat.messenger.core.http.api.accountupdate

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.typeRef

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
