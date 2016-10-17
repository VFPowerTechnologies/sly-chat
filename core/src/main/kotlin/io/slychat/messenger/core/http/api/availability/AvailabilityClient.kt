package io.slychat.messenger.core.http.api.availability

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.typeRef

class AvailabilityClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun checkEmailAvailability(email: String): Boolean {
        val url = "$serverBaseUrl/v1/availability/email/$email"

        return apiGetRequest(httpClient, url, null, emptyList(), typeRef())
    }

    fun checkPhoneNumberAvailability(phoneNumber: String): Boolean {
        val url = "$serverBaseUrl/v1/availability/phone-number/$phoneNumber"

        return apiGetRequest(httpClient, url, null, emptyList(), typeRef())
    }
}