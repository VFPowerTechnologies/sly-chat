package io.slychat.messenger.core.http.api.pushnotifications

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface PushNotificationsAsyncClient {
    fun isRegistered(userCredentials: UserCredentials): Promise<IsRegisteredResponse, Exception>

    fun register(userCredentials: UserCredentials, request: RegisterRequest): Promise<RegisterResponse, Exception>

    fun unregister(userCredentials: UserCredentials): Promise<Unit, Exception>

    fun unregister(request: UnregisterRequest): Promise<Unit, Exception>
}

