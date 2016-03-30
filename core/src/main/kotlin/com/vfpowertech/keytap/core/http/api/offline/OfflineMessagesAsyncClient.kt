package com.vfpowertech.keytap.core.http.api.offline

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class OfflineMessagesAsyncClient(private val serverUrl: String) {
    private fun newClient(): OfflineMessagesClient = OfflineMessagesClient(serverUrl, JavaHttpClient())

    fun get(request: OfflineMessagesGetRequest): Promise<OfflineMessagesGetResponse, Exception> = task {
        newClient().get(request)
    }

    fun clear(request: OfflineMessagesClearRequest): Promise<Unit, Exception> = task {
        newClient().clear(request)
    }
}