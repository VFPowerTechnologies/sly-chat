package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.contacts.ContactClient
import com.vfpowertech.keytap.core.http.api.contacts.FetchContactResponse
import com.vfpowertech.keytap.core.http.api.contacts.NewContactRequest
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ContactClientWrapper(serverUrl: String) {
    private val contactClient = ContactClient(serverUrl, JavaHttpClient())

    fun fetchNewContactInfo(request: NewContactRequest): Promise<FetchContactResponse, Exception> = task {
        val apiResponse = contactClient.fetchContactInfo(request)

        apiResponse.getOrThrow { it }
    }
}