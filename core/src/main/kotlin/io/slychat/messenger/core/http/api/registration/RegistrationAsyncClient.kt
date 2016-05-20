package io.slychat.messenger.core.http.api.registration

import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneRequest
import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneResponse
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class RegistrationAsyncClient(private val serverUrl: String) {
    private fun newClient() = RegistrationClient(serverUrl, io.slychat.messenger.core.http.JavaHttpClient())

    fun register(request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        newClient().register(request)
    }

    fun verifySmsCode(request: SmsVerificationRequest): Promise<SmsVerificationResponse, Exception> = task {
        newClient().verifySmsCode(request)
    }

    fun resendSmsCode(request: SmsResendRequest): Promise<SmsVerificationResponse, Exception> = task {
        newClient().resendSmsCode(request)
    }

    fun updatePhone(request: UpdatePhoneRequest): Promise<UpdatePhoneResponse, Exception> = task {
        newClient().updatePhone(request)
    }
}