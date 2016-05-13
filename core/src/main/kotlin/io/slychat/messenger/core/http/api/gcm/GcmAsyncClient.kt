package io.slychat.messenger.core.http.api.gcm

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class GcmAsyncClient(private val serverUrl: String) {
    private fun newClient(): GcmClient = GcmClient(serverUrl, io.slychat.messenger.core.http.JavaHttpClient())

    fun register(userCredentials: UserCredentials, request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        newClient().register(userCredentials, request)
    }

    fun unregister(userCredentials: UserCredentials, request: UnregisterRequest): Promise<Unit, Exception> = task {
        newClient().unregister(userCredentials, request)
    }
}