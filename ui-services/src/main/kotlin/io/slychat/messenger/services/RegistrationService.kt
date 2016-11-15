package io.slychat.messenger.services

import io.slychat.messenger.services.ui.UIRegistrationInfo
import io.slychat.messenger.services.ui.UISmsVerificationStatus
import io.slychat.messenger.services.ui.UIUpdatePhoneInfo
import io.slychat.messenger.services.ui.UIUpdatePhoneResult
import nl.komponents.kovenant.Promise
import rx.Observable


interface RegistrationService {
    /** Events are emitted on the main thread. */
    val registrationEvents: Observable<RegistrationProgress>

    /** Register a new account with the given info */
    fun doRegistration(info: UIRegistrationInfo)

    fun submitVerificationCode(username: String, code: String): Promise<UISmsVerificationStatus, Exception>

    fun resendVerificationCode(username: String): Promise<UISmsVerificationStatus, Exception>

    fun checkEmailAvailability(email: String): Promise<Boolean, Exception>

    fun checkPhoneNumberAvailability(phoneNumber: String): Promise<Boolean, Exception>

    /** Update phone with the given info */
    fun updatePhone(info: UIUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception>

    /** Restart registration event state. Must not be called if a registration is currently processing. */
    fun resetState()
}