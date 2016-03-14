package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactAsyncClient(serverUrl: String) {
    private val contactClient = ContactClient(serverUrl, JavaHttpClient())

    fun fetchNewContactInfo(request: NewContactRequest): Promise<FetchContactResponse, Exception> = task {
         contactClient.fetchContactInfo(request)
    }

    fun findLocalContacts(request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception> = task {
        contactClient.findLocalContacts(request)
    }
}