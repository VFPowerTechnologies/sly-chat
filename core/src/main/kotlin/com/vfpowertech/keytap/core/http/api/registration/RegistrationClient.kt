package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.valueFromApi
import com.vfpowertech.keytap.core.typeRef

/**
 * @param serverBaseUrl protocol://hostname[:port] with no trailing slash
 */
class RegistrationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/register"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<RegisterResponse>>())
    }

    fun verifySmsCode(request: SmsVerificationRequest): SmsVerificationResponse {
        val url = "$serverBaseUrl/v1/sms/verification"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<SmsVerificationResponse>>())
    }

    fun resendSmsCode(request: SmsResendRequest): SmsVerificationResponse {
        val url = "$serverBaseUrl/v1/sms/resend"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return valueFromApi(resp, setOf(200, 400), typeRef<ApiResult<SmsVerificationResponse>>())
    }
}
