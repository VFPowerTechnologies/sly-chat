package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface ContactLookupAsyncClient {
    fun find(userCredentials: UserCredentials, request: FindContactRequest): Promise<FindContactResponse, Exception>

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception>

    fun findAllById(userCredentials: UserCredentials, request: FindAllByIdRequest): Promise<FindAllByIdResponse, Exception>

    fun findById(userCredentials: UserCredentials, userId: UserId): Promise<FindByIdResponse, Exception>
}