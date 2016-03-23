package com.vfpowertech.keytap.core.http.api.registration

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class RegistrationAsyncClient(private val serverUrl: String) {
    private fun newClient() = RegistrationClient(serverUrl, JavaHttpClient())

    fun register(request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        newClient().register(request)
    }

    fun verifySmsCode(request: SmsVerificationRequest): Promise<SmsVerificationResponse, Exception> = task {
        newClient().verifySmsCode(request)
    }

    fun resendSmsCode(request: SmsResendRequest): Promise<SmsVerificationResponse, Exception> = task {
        newClient().resendSmsCode(request)
    }
}