package io.slychat.messenger.core.http.api.offline

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class OfflineMessagesAsyncClient(private val serverUrl: String) {
    private fun newClient(): OfflineMessagesClient = OfflineMessagesClient(serverUrl, io.slychat.messenger.core.http.JavaHttpClient())

    fun get(userCredentials: UserCredentials): Promise<OfflineMessagesGetResponse, Exception> = task {
        newClient().get(userCredentials)
    }

    fun clear(userCredentials: UserCredentials, request: OfflineMessagesClearRequest): Promise<Unit, Exception> = task {
        newClient().clear(userCredentials, request)
    }
}