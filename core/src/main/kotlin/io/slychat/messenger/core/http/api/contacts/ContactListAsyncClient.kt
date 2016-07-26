package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface ContactListAsyncClient {
    fun getContacts(userCredentials: UserCredentials): Promise<GetContactsResponse, Exception>

    fun updateContacts(userCredentials: UserCredentials, request: UpdateContactsRequest): Promise<Unit, Exception>
}