package io.slychat.messenger.services

import nl.komponents.kovenant.Promise

interface AuthenticationService {
    fun auth(emailOrPhoneNumber: String, password: String, registrationId: Int): Promise<AuthResult, Exception>
}