package com.vfpowertech.keytap.core.http.api.gcm

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class GcmAsyncClient(private val serverUrl: String) {
    private fun newClient(): GcmClient = GcmClient(serverUrl, JavaHttpClient())

    fun register(request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        newClient().register(request)
    }

    fun unregister(request: UnregisterRequest): Promise<Unit, Exception> = task {
        newClient().unregister(request)
    }
}
