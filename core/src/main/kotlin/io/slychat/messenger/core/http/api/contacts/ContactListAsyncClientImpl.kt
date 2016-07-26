package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactListAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : ContactListAsyncClient {
    private fun newClient() = ContactListClient(serverUrl, factory.create())

    override fun addContacts(userCredentials: UserCredentials, request: AddContactsRequest): Promise<Unit, Exception> = task {
        newClient().addContacts(userCredentials, request)
    }

    override fun removeContacts(userCredentials: UserCredentials, request: RemoveContactsRequest): Promise<Unit, Exception> = task {
        newClient().removeContacts(userCredentials, request)
    }

    override fun getContacts(userCredentials: UserCredentials): Promise<GetContactsResponse, Exception> = task {
        newClient().getContacts(userCredentials)
    }

    override fun updateContacts(userCredentials: UserCredentials, request: UpdateContactsRequest): Promise<Unit, Exception> = task {
        newClient().updateContacts(userCredentials, request)
    }
}