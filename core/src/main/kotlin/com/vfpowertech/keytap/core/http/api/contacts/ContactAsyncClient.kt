package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactAsyncClient(private val serverUrl: String) {
    private fun newClient() = ContactClient(serverUrl, JavaHttpClient())

    fun fetchNewContactInfo(userCredentials: UserCredentials, request: NewContactRequest): Promise<FetchContactResponse, Exception> = task {
         newClient().fetchContactInfo(userCredentials, request)
    }

    fun findLocalContacts(userCredentials: UserCredentials, request: FindLocalContactsRequest): Promise<FindLocalContactsResponse, Exception> = task {
        newClient().findLocalContacts(userCredentials, request)
    }

    fun fetchContactInfoByEmail(userCredentials: UserCredentials, request: FetchContactInfoByIdRequest): Promise<FetchContactInfoByIdResponse, Exception> = task {
        newClient().fetchContactInfoById(userCredentials, request)
    }
}