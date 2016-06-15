package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactListAsyncClient(private val serverUrl: String, private val factory: HttpClientFactory) {
    private fun newClient() = ContactListClient(serverUrl, factory.create())

    fun addContacts(userCredentials: UserCredentials, request: AddContactsRequest): Promise<Unit, Exception> = task {
        newClient().addContacts(userCredentials, request)
    }

    fun removeContacts(userCredentials: UserCredentials, request: RemoveContactsRequest): Promise<Unit, Exception> = task {
        newClient().removeContacts(userCredentials, request)
    }

    fun getContacts(userCredentials: UserCredentials): Promise<GetContactsResponse, Exception> = task {
        newClient().getContacts(userCredentials)
    }

    fun updateContacts(userCredentials: UserCredentials, request: UpdateContactsRequest): Promise<Unit, Exception> = task {
        newClient().updateContacts(userCredentials, request)
    }
}