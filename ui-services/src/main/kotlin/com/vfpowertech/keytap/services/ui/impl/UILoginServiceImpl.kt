package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationParamsResponse
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.UserLoginData
import com.vfpowertech.keytap.services.ui.UILoginResult
import com.vfpowertech.keytap.services.ui.UILoginService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.KovenantUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

/** Map a promise with the body running on the main ui thread. */

class UILoginServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String
) : UILoginService {

    override fun logout() {
        app.destroyUserSession()
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val loginClient = AuthenticationClientWrapper(serverUrl)

    private fun continueLogin(emailOrPhoneNumber: String, password: String, response: AuthenticationParamsResponse): Promise<UILoginResult, Exception> {
        if (response.errorMessage != null)
            return Promise.ofSuccess(UILoginResult(false, response.errorMessage))

        val authParams = response.params!!

        val hashParams = HashDeserializers.deserialize(authParams.hashParams)
        val hash = hashPasswordWithParams(password, hashParams)

        KovenantUi.uiContext

        val request = AuthenticationRequest(emailOrPhoneNumber, hash.hexify(), authParams.csrfToken)
        return loginClient.auth(request) successUi { response ->
            val data = response.data
            if (data != null) {
                //TODO need to put the username in the login response if the user used their phone number
                val keyVault = KeyVault.deserialize(data.keyVault, password)
                app.createUserSession(UserLoginData(emailOrPhoneNumber, keyVault, data.authToken))

                app.storeAccountData(keyVault)

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