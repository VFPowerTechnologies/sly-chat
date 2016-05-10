package com.vfpowertech.keytap.core.http.api.registration

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.accountupdate.UpdatePhoneRequest
import com.vfpowertech.keytap.core.http.api.accountupdate.UpdatePhoneResponse
import com.vfpowertech.keytap.core.http.api.apiPostRequest2
import com.vfpowertech.keytap.core.typeRef

/**
 * @param serverBaseUrl protocol://hostname[:port] with no trailing slash
 */
class RegistrationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/register"

        return apiPostRequest2(httpClient, url, null, request, setOf(200, 400), typeRef())
    }

    fun verifySmsCode(request: SmsVerificationRequest): SmsVerificationResponse {
        val url = "$serverBaseUrl/v1/sms/verification"

        return apiPostRequest2(httpClient, url, null, request, setOf(200, 400), typeRef())
    }

    fun resendSmsCode(request: SmsResendRequest): SmsVerificationResponse {
        val url = "$serverBaseUrl/v1/sms/resend"

        return apiPostRequest2(httpClient, url, null, request, setOf(200, 400), typeRef())
    }

    fun updatePhone(request: UpdatePhoneRequest): UpdatePhoneResponse {
        val url = "$serverBaseUrl/v1/account/update/phone"

        return apiPostRequest2(httpClient, url, null, request, setOf(200, 400), typeRef())
    }
}
