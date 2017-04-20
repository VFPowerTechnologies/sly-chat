package io.slychat.messenger.services

import io.slychat.messenger.core.crypto.hashes.HashType
import io.slychat.messenger.core.crypto.hashes.hashPasswordWithParams
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneRequest
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClient
import io.slychat.messenger.core.http.api.availability.AvailabilityAsyncClient
import io.slychat.messenger.core.http.api.registration.*
import io.slychat.messenger.services.auth.AuthApiResponseException
import io.slychat.messenger.services.ui.UIRegistrationInfo
import io.slychat.messenger.services.ui.UISmsVerificationStatus
import io.slychat.messenger.services.ui.UIUpdatePhoneInfo
import io.slychat.messenger.services.ui.UIUpdatePhoneResult
import io.slychat.messenger.services.ui.impl.asyncGenerateNewKeyVault
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subjects.BehaviorSubject

class RegistrationServiceImpl(
    scheduler: Scheduler,
    private val registrationClient: RegistrationAsyncClient,
    private val loginClient: AuthenticationAsyncClient,
    private val availabilityClient: AvailabilityAsyncClient
) : RegistrationService {
    private val log = LoggerFactory.getLogger(javaClass)

    private var inProgress = false
    private val subject = BehaviorSubject.create<RegistrationProgress>(RegistrationProgress.Waiting())
    override val registrationEvents: Observable<RegistrationProgress> = subject.observeOn(scheduler)

    override fun doRegistration(info: UIRegistrationInfo) {
        if (inProgress)
            error("doRegistration called while registration still active")

        val username = info.email
        val password = info.password

        subject.onNext(RegistrationProgress.Update("Generating key vault"))

        asyncGenerateNewKeyVault(password) bind { keyVault ->
            subject.onNext(RegistrationProgress.Update("Connecting to server..."))

            val registrationInfo = RegistrationInfo(username, info.name, info.phoneNumber)
            val request = registrationRequestFromKeyVault(registrationInfo, keyVault, password)
            registrationClient.register(request)
        } successUi {
            subject.onNext(RegistrationProgress.Complete(it.isSuccess, it.errorMessage, it.validationErrors))

            inProgress = false
        } failUi {
            log.warn("Registration failed: {}", it.message, it)

            subject.onNext(RegistrationProgress.Error(it))

            inProgress = false
        }
    }

    override fun resetState() {
        if (inProgress)
            error("resetState called while registration still active")

        subject.onNext(RegistrationProgress.Waiting())
    }

    override fun submitVerificationCode(username: String, code: String): Promise<UISmsVerificationStatus, Exception> {
        return registrationClient.verifySmsCode(SmsVerificationRequest(username, code)) map { response ->
            UISmsVerificationStatus(response.isSuccess, response.errorMessage)
        }
    }

    override fun resendVerificationCode(username: String): Promise<UISmsVerificationStatus, Exception> {
        return registrationClient.resendSmsCode(SmsResendRequest(username)) map { response ->
            UISmsVerificationStatus(response.isSuccess, response.errorMessage)
        }
    }

    override fun checkEmailAvailability(email: String): Promise<Boolean, Exception> {
        return availabilityClient.checkEmailAvailability(email)
    }

    override fun checkPhoneNumberAvailability(phoneNumber: String): Promise<Boolean, Exception> {
        return availabilityClient.checkPhoneNumberAvailability(phoneNumber)
    }

    override fun updatePhone(info: UIUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception> {
        return loginClient.getParams(info.email) bind { response ->
            val errorMessage = response.errorMessage
            if (errorMessage != null)
                throw AuthApiResponseException(errorMessage)

            val authParams = response.params!!

            val remotePasswordHash = hashPasswordWithParams(info.password, authParams.hashParams, HashType.REMOTE)

            registrationClient.updatePhone(UpdatePhoneRequest(info.email, remotePasswordHash.hexify(), info.phoneNumber)) map { response ->
                UIUpdatePhoneResult(response.isSuccess, response.errorMessage)
            }
        }
    }
}