package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.persistence.ContactInfo
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : ContactAsyncClient {
    private fun newClient() = ContactClient(serverUrl, factory.create())

    override fun fetchNewContactInfo(userCredentials: UserCredentials, request: NewContactRequest): Promise<FetchContactResponse, Exception> = task {
         newClient().fetchContactInfo(userCredentials, request)
    }

    override fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception> = task {
        newClient().findLocalContacts(userCredentials, request)
    }

    override fun fetchMultiContactInfoById(userCredentials: UserCredentials, request: FetchMultiContactInfoByIdRequest): Promise<FetchMultiContactInfoByIdResponse, Exception> = task {
        newClient().fetchMultiContactInfoById(userCredentials, request)
    }

    override fun fetchContactInfoById(userCredentials: UserCredentials, userId: UserId): Promise<FetchContactInfoByIdResponse, Exception> = task {
        newClient().fetchContactInfoById(userCredentials, userId)
    }
}