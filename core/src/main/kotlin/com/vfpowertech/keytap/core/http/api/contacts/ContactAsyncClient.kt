package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactAsyncClient(private val serverUrl: String) {
    private fun newClient() = ContactClient(serverUrl, JavaHttpClient())

    fun fetchNewContactInfo(request: NewContactRequest): Promise<FetchContactResponse, Exception> = task {
         newClient().fetchContactInfo(request)
    }

    fun findLocalContacts(request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception> = task {
        newClient().findLocalContacts(request)
    }

    fun fetchContactInfoByEmail(request: FetchContactInfoByIdRequest): Promise<FetchContactInfoByIdResponse, Exception> = task {
        newClient().fetchContactInfoById(request)
    }
}