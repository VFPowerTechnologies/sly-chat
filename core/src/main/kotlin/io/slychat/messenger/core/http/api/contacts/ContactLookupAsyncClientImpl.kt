package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactLookupAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : ContactLookupAsyncClient {
    private fun newClient() = ContactLookupClient(serverUrl, factory.create())

    override fun find(userCredentials: UserCredentials, request: FindContactRequest): Promise<FindContactResponse, Exception> = task {
         newClient().find(userCredentials, request)
    }

    override fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception> = task {
        newClient().findLocalContacts(userCredentials, request)
    }

    override fun findAllById(userCredentials: UserCredentials, request: FindAllByIdRequest): Promise<FindAllByIdResponse, Exception> = task {
        newClient().findAllById(userCredentials, request)
    }

    override fun findById(userCredentials: UserCredentials, userId: UserId): Promise<FindByIdResponse, Exception> = task {
        newClient().findById(userCredentials, userId)
    }
}