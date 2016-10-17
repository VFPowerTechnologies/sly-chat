package io.slychat.messenger.core.http.api.availability

import nl.komponents.kovenant.Promise

interface AvailabilityAsyncClient {
    fun checkEmailAvailability(email: String): Promise<Boolean, Exception>
    fun checkPhoneNumberAvailability(phoneNumber: String): Promise<Boolean, Exception>
}
