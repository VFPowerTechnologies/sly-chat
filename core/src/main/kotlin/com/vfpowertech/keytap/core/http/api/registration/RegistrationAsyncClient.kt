package com.vfpowertech.keytap.core.http.api.registration

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationAsyncClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class RegistrationAsyncClient(serverUrl: String) {
    private val registrationClient = RegistrationClient(serverUrl, JavaHttpClient())

    fun register(request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        registrationClient.register(request)
    }

    fun verifySmsCode(request: SmsVerificationRequest): Promise<SmsVerificationResponse, Exception> = task {
        registrationClient.verifySmsCode(request)
    }

    fun resendSmsCode(request: SmsResendRequest): Promise<SmsVerificationResponse, Exception> = task {
        registrationClient.resendSmsCode(request)
    }

}