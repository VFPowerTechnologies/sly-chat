package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class PreKeyStoreAsyncClient(private val serverUrl: String) {
    private fun newClient() = PreKeyStorageClient(serverUrl, JavaHttpClient())

    fun store(request: PreKeyStoreRequest): Promise<PreKeyStoreResponse, Exception> = task {
        newClient().store(request)
    }
}