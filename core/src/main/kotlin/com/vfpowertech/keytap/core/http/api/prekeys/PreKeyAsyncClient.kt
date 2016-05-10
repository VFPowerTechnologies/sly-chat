package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.relay.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class PreKeyAsyncClient(private val serverUrl: String) {
    private fun newClient() = PreKeyClient(serverUrl, JavaHttpClient())

    fun retrieve(userCredentials: UserCredentials, request: PreKeyRetrievalRequest): Promise<PreKeyRetrievalResponse, Exception> = task {
        newClient().retrieve(userCredentials, request)
    }

    fun store(userCredentials: UserCredentials, request: PreKeyStoreRequest): Promise<PreKeyStoreResponse, Exception> = task {
        newClient().store(userCredentials, request)
    }

    fun getInfo(userCredentials: UserCredentials): Promise<PreKeyInfoResponse, Exception> = task {
        newClient().getInfo(userCredentials)
    }
}