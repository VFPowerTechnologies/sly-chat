package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AddressBookAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : AddressBookAsyncClient {
    private fun newClient() = AddressBookClient(serverUrl, factory.create())

    override fun get(userCredentials: UserCredentials, request: GetAddressBookRequest): Promise<GetAddressBookResponse, Exception> = task {
        newClient().get(userCredentials, request)
    }

    override fun update(userCredentials: UserCredentials, request: UpdateAddressBookRequest): Promise<UpdateAddressBookResponse, Exception> = task {
        newClient().update(userCredentials, request)
    }
}