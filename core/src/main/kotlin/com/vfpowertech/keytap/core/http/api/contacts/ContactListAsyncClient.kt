package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactListAsyncClient(serverUrl: String) {
    private val contactListClient = ContactListClient(serverUrl, JavaHttpClient())

    fun addContacts(request: AddContactsRequest): Promise<Unit, Exception> = task {
        contactListClient.addContacts(request)
    }

    fun removeContacts(request: RemoveContactsRequest): Promise<Unit, Exception> = task {
        contactListClient.removeContacts(request)

    }

    fun getContacts(request: GetContactsRequest): Promise<GetContactsResponse, Exception> = task {
        contactListClient.getContacts(request)
    }
}