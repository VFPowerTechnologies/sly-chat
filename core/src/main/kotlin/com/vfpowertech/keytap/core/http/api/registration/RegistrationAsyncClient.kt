package com.vfpowertech.keytap.core.http.api.registration

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class RegistrationAsyncClient(serverUrl: String) {
    private val registrationClient = RegistrationClient(serverUrl, JavaHttpClient())

    fun register(request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        registrationClient.register(request)
    }
}