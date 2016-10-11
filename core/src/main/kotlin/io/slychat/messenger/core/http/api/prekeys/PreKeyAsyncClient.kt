package io.slychat.messenger.core.http.api.prekeys

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class PreKeyAsyncClient(private val serverUrl: String, private val factory: HttpClientFactory) {
    private fun newClient() = HttpPreKeyClient(serverUrl, factory.create())

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