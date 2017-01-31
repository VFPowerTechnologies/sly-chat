package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.enforceExhaustive
import io.slychat.messenger.services.RegistrationProgress
import io.slychat.messenger.services.RegistrationService
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import java.util.*

class UIRegistrationServiceImpl(
    val registrationService: RegistrationService
) : UIRegistrationService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val listeners = ArrayList<(String) -> Unit>()

    private var currentRegistration: Deferred<UIRegistrationResult, Exception>? = null

    init {
        registrationService.registrationEvents.subscribe { onRegistrationEvent(it) }
    }

    private fun withCurrentDeferred(body: (Deferred<UIRegistrationResult, Exception>) -> Unit) {
        val d = currentRegistration
        currentRegistration = null
        if (d != null)
            body(d)
        else
            log.error("Registration complete received, but no deferred available")
    }

    private fun onRegistrationEvent(event: RegistrationProgress) {
        when (event) {
            is RegistrationProgress.Waiting -> {}

            is RegistrationProgress.Update -> updateProgress(event.progressText)

            is RegistrationProgress.Complete -> withCurrentDeferred {
                it.resolve(UIRegistrationResult(event.successful, event.errorMessage, event.validationErrors))
            }

            is RegistrationProgress.Error -> withCurrentDeferred {
                it.reject(event.cause)
            }
        }.enforceExhaustive()
    }

    override fun doRegistration(info: UIRegistrationInfo): Promise<UIRegistrationResult, Exception> {
        val d = deferred<UIRegistrationResult, Exception>()
        currentRegistration = d
        registrationService.doRegistration(info)

        return d.promise
    }

    //TODO need a better reporting setup
    private fun updateProgress(status: String) {
        for (listener in listeners)
            listener(status)
    }

    override fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    override fun submitVerificationCode(username: String, code: String): Promise<UISmsVerificationStatus, Exception> {
        return registrationService.submitVerificationCode(username, code)
    }

    override fun resendVerificationCode(username: String): Promise<UISmsVerificationStatus, Exception> {
        return registrationService.resendVerificationCode(username)
    }

    override fun checkEmailAvailability(email: String): Promise<Boolean, Exception> {
        return registrationService.checkEmailAvailability(email)
    }

    override fun checkPhoneNumberAvailability(phoneNumber: String): Promise<Boolean, Exception> {
        return registrationService.checkPhoneNumberAvailability(phoneNumber)
    }

    override fun updatePhone(info: UIUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception> {
        return registrationService.updatePhone(info)
    }

    override fun clearListeners() {
        listeners.clear()
    }
}