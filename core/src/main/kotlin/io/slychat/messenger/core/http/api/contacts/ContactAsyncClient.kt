package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface ContactAsyncClient {
    fun fetchNewContactInfo(userCredentials: UserCredentials, request: NewContactRequest): Promise<FetchContactResponse, Exception>

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception>

    fun fetchContactInfoById(userCredentials: UserCredentials, request: FetchContactInfoByIdRequest): Promise<FetchContactInfoByIdResponse, Exception>
}