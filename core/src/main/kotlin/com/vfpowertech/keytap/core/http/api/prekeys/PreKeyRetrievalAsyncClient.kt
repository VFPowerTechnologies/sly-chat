package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class PreKeyRetrievalAsyncClient(private val serverUrl: String) {
    private fun newClient() = PreKeyRetrievalClient(serverUrl, JavaHttpClient())

    fun retrieve(request: PreKeyRetrievalRequest): Promise<PreKeyRetrievalResponse, Exception> = task {
        newClient().retrieve(request)
    }
}