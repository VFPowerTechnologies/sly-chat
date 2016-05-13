package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactAsyncClient(private val serverUrl: String) {
    private fun newClient() = ContactClient(serverUrl, io.slychat.messenger.core.http.JavaHttpClient())

    fun fetchNewContactInfo(userCredentials: UserCredentials, request: NewContactRequest): Promise<FetchContactResponse, Exception> = task {
         newClient().fetchContactInfo(userCredentials, request)
    }

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception> = task {
        newClient().findLocalContacts(userCredentials, request)
    }

    fun fetchContactInfoByEmail(userCredentials: UserCredentials, request: FetchContactInfoByIdRequest): Promise<FetchContactInfoByIdResponse, Exception> = task {
        newClient().fetchContactInfoById(userCredentials, request)
    }
}