package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.http.api.registration.RegistrationAsyncClient
import com.vfpowertech.keytap.core.http.api.registration.RegistrationInfo
import com.vfpowertech.keytap.core.http.api.registration.registrationRequestFromKeyVault
import com.vfpowertech.keytap.services.ui.UIRegistrationInfo
import com.vfpowertech.keytap.services.ui.UIRegistrationResult
import com.vfpowertech.keytap.services.ui.UIRegistrationService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import java.util.*

class UIRegistrationServiceImpl(
    serverUrl: String
) : UIRegistrationService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val listeners = ArrayList<(String) -> Unit>()
    private val registrationClient = RegistrationAsyncClient(serverUrl)

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
}