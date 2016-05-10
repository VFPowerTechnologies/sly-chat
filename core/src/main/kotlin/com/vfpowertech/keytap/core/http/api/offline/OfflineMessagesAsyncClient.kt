package com.vfpowertech.keytap.core.http.api.offline

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class OfflineMessagesAsyncClient(private val serverUrl: String) {
    private fun newClient(): OfflineMessagesClient = OfflineMessagesClient(serverUrl, JavaHttpClient())

    fun get(userCredentials: UserCredentials): Promise<OfflineMessagesGetResponse, Exception> = task {
        newClient().get(userCredentials)
    }

    fun clear(userCredentials: UserCredentials, request: OfflineMessagesClearRequest): Promise<Unit, Exception> = task {
        newClient().clear(userCredentials, request)
    }
}