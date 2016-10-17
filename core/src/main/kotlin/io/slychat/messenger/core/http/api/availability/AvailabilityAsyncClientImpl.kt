package io.slychat.messenger.core.http.api.availability

import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AvailabilityAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : AvailabilityAsyncClient {
    private fun newClient() = AvailabilityClient(serverUrl, factory.create())

    override fun checkEmailAvailability(email: String): Promise<Boolean, Exception> = task {
        newClient().checkEmailAvailability(email)
    }

    override fun checkPhoneNumberAvailability(phoneNumber: String): Promise<Boolean, Exception> = task {
        newClient().checkPhoneNumberAvailability(phoneNumber)
    }
}