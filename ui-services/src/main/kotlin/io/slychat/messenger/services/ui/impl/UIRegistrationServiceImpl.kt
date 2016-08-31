package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.crypto.HashDeserializers
import io.slychat.messenger.core.crypto.hashPasswordWithParams
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneRequest
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClient
import io.slychat.messenger.core.http.api.registration.*
import io.slychat.messenger.services.auth.AuthApiResponseException
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import java.util.*

class UIRegistrationServiceImpl(
    private val registrationClient: RegistrationAsyncClient,
    private val loginClient: AuthenticationAsyncClient
) : UIRegistrationService {
    private val listeners = ArrayList<(String) -> Unit>()

    override fun doRegistration(info: UIRegistrationInfo): Promise<UIRegistrationResult, Exception> {
        val username = info.email
        val password = info.password

        //we don't need to write the key vault to disk here, as we receive it during login
        //on failure we just discard it
        updateProgress("Generating key vault")
        return asyncGenerateNewKeyVault(password) bind { keyVault ->
            updateProgress("Connecting to server...")
            val registrationInfo = RegistrationInfo(username, info.name, info.phoneNumber)
            val request = registrationRequestFromKeyVault(registrationInfo, keyVault)
            registrationClient.register(request)
        } map { result ->
            val uiResult = UIRegistrationResult(result.isSuccess, result.errorMessage, result.validationErrors)
            if (uiResult.successful) {
                updateProgress("Registration complete")
            }
            else {
                updateProgress("An error occured during registration")
            }
            uiResult
        }
    }

    //TODO need a better reporting setup
    private fun updateProgress(status: String) {
        synchronized(this) {
            for (listener in listeners)
                listener(status)
        }
    }

    override fun addListener(listener: (String) -> Unit) {
        synchronized(this) {
            listeners.add(listener)
        }
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

    override fun updatePhone(info: UIUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception> {
        return loginClient.getParams(info.email) bind { response ->
            if (response.errorMessage != null)
                throw AuthApiResponseException(response.errorMessage)

            val authParams = response.params!!

            val hashParams = HashDeserializers.deserialize(authParams.hashParams)
            val hash = hashPasswordWithParams(info.password, hashParams)

            registrationClient.updatePhone(UpdatePhoneRequest(info.email, hash.hexify(), info.phoneNumber)) map { response ->
                UIUpdatePhoneResult(response.isSuccess, response.errorMessage)
            }
        }
    }
}