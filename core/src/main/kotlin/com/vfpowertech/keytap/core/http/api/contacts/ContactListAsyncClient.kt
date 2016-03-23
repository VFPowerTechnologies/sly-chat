package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactListAsyncClient(private val serverUrl: String) {
    private fun newClient() = ContactListClient(serverUrl, JavaHttpClient())

    fun addContacts(request: AddContactsRequest): Promise<Unit, Exception> = task {
        newClient().addContacts(request)
    }

    fun removeContacts(request: RemoveContactsRequest): Promise<Unit, Exception> = task {
        newClient().removeContacts(request)
    }

    fun getContacts(request: GetContactsRequest): Promise<GetContactsResponse, Exception> = task {
        newClient().getContacts(request)
    }
}