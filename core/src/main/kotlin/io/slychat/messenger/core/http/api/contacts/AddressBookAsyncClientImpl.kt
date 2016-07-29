package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AddressBookAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : AddressBookAsyncClient {
    private fun newClient() = AddressBookClient(serverUrl, factory.create())

    override fun getContacts(userCredentials: UserCredentials): Promise<GetAddressBookResponse, Exception> = task {
        newClient().get(userCredentials)
    }

    override fun updateContacts(userCredentials: UserCredentials, request: UpdateAddressBookRequest): Promise<Unit, Exception> = task {
        newClient().update(userCredentials, request)
    }
}