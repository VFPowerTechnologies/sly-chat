package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactListAsyncClient(private val serverUrl: String) {
    private fun newClient() = ContactListClient(serverUrl, JavaHttpClient())

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