package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface ContactAsyncClient {
    fun fetchNewContactInfo(userCredentials: UserCredentials, request: NewContactRequest): Promise<FetchContactResponse, Exception>

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception>

    fun fetchMultiContactInfoById(userCredentials: UserCredentials, request: FetchMultiContactInfoByIdRequest): Promise<FetchMultiContactInfoByIdResponse, Exception>

    fun fetchContactInfoById(userCredentials: UserCredentials, userId: UserId): Promise<FetchContactInfoByIdResponse, Exception>
}