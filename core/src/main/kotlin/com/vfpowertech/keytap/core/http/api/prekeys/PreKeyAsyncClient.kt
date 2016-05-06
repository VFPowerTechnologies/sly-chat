package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class PreKeyAsyncClient(private val serverUrl: String) {
    private fun newClient() = PreKeyClient(serverUrl, JavaHttpClient())

    fun retrieve(request: PreKeyRetrievalRequest): Promise<PreKeyRetrievalResponse, Exception> = task {
        newClient().retrieve(request)
    }

    fun store(request: PreKeyStoreRequest): Promise<PreKeyStoreResponse, Exception> = task {
        newClient().store(request)
    }

    fun getInfo(request: PreKeyInfoRequest): Promise<PreKeyInfoResponse, Exception> = task {
        newClient().getInfo(request)
    }
}