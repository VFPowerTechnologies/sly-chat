package io.slychat.messenger.core.http.api.registration

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneRequest
import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneResponse
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

/**
 * @param serverBaseUrl protocol://hostname[:port] with no trailing slash
 */
class RegistrationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/registration"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }

    fun verifySmsCode(request: SmsVerificationRequest): SmsVerificationResponse {
        val url = "$serverBaseUrl/v1/registration/verification"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }

    fun resendSmsCode(request: SmsResendRequest): SmsVerificationResponse {
        val url = "$serverBaseUrl/v1/registration/verification/resend"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }

    fun updatePhone(request: UpdatePhoneRequest): UpdatePhoneResponse {
        val url = "$serverBaseUrl/v1/registration/phone-number"

        return apiPostRequest(httpClient, url, null, request, typeRef())
    }
}
