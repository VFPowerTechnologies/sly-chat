package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactListAsyncClient(private val serverUrl: String) {
    private fun newClient() = ContactListClient(serverUrl, io.slychat.messenger.core.http.JavaHttpClient())

    fun addContacts(userCredentials: UserCredentials, request: AddContactsRequest): Promise<Unit, Exception> = task {
        newClient().addContacts(userCredentials, request)
    }

    fun removeContacts(userCredentials: UserCredentials, request: RemoveContactsRequest): Promise<Unit, Exception> = task {
        newClient().removeContacts(userCredentials, request)
    }

    fun getContacts(userCredentials: UserCredentials): Promise<GetContactsResponse, Exception> = task {
        newClient().getContacts(userCredentials)
    }
}