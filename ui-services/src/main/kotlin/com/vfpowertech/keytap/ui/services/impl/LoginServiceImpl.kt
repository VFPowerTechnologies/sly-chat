package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationParamsResponse
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.persistence.KeyVaultPersistenceManager
import com.vfpowertech.keytap.ui.services.KeyTapApplication
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.UILoginResult
import com.vfpowertech.keytap.ui.services.UserLoginData
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class LoginServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String,
    private val keyVaultPersistenceManager: KeyVaultPersistenceManager
) : LoginService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val loginClient = AuthenticationClientWrapper(serverUrl)

    private fun continueLogin(emailOrPhoneNumber: String, password: String, response: AuthenticationParamsResponse): Promise<UILoginResult, Exception> {
        if (response.errorMessage != null)
            return Promise.ofSuccess(UILoginResult(false, response.errorMessage))

        val authParams = response.params!!

        val hashParams = HashDeserializers.deserialize(authParams.hashParams)
        val hash = hashPasswordWithParams(password, hashParams)

        val request = AuthenticationRequest(emailOrPhoneNumber, hash.hexify(), authParams.csrfToken)
        return loginClient.auth(request) bind { response ->
            val data = response.data
            if (data != null) {
                val serializedKeyVault = data.keyVault
                val keyVault = KeyVault.deserialize(serializedKeyVault, password)
                keyVaultPersistenceManager.store(keyVault) map { response }
            }
            else
                Promise.ofSuccess(response)
        } successUi { response ->
            val data = response.data
            if (data != null) {
                //TODO need to put the username in the login response if the user used their phone number
                //TODO remove keyvault deserialization dup; successUi doesn't let us return any promises to chain though
                val keyVault = KeyVault.deserialize(data.keyVault, password)
                app.createUserSession(UserLoginData(emailOrPhoneNumber, keyVault, data.authToken))

                if (data.keyRegenCount > 0) {
                    //TODO schedule prekey upload in bg
                    logger.info("Requested to generate {} new prekeys", data.keyRegenCount)
                }
            }
        } map { response ->
            if (response.isSuccess)
                UILoginResult(true, null)
            else
                UILoginResult(false, response.errorMessage)
        }
    }

    override fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception> {
        return loginClient.getParams(emailOrPhoneNumber) bind { continueLogin(emailOrPhoneNumber, password, it) }
    }
}