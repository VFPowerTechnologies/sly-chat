package io.slychat.messenger.core.http.api.prekeys

import io.slychat.messenger.core.UserCredentials

interface PreKeyClient {
    fun retrieve(userCredentials: UserCredentials, request: PreKeyRetrievalRequest): PreKeyRetrievalResponse
    fun store(userCredentials: UserCredentials, request: PreKeyStoreRequest): PreKeyStoreResponse
    fun getInfo(userCredentials: UserCredentials): PreKeyInfoResponse
}