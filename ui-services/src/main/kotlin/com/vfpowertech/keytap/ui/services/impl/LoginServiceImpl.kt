package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationParamsResponse
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.UILoginResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class LoginServiceImpl(serverUrl: String) : LoginService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val loginClient = AuthenticationClientWrapper(serverUrl)

    private fun continueLogin(emailOrPhoneNumber: String, password: String, response: AuthenticationParamsResponse): Promise<UILoginResult, Exception> {
        if (response.errorMessage != null)
            return Promise.ofSuccess(UILoginResult(false, response.errorMessage))

        val authParams = response.params!!

        val hashParams = HashDeserializers.deserialize(authParams.hashParams)
        val hash = hashPasswordWithParams(password, hashParams)

        val request = AuthenticationRequest(emailOrPhoneNumber, hash.hexify(), authParams.csrfToken)
        return loginClient.auth(request) successUi { response ->
            if (response.isSuccess) {
                //TODO
                CredentialsManager.authToken = response.authToken

                if (response.keyRegenCount > 0) {
                    //TODO schedule prekey upload in bg
                    logger.info("Requested to generate {} new prekeys", response.keyRegenCount)
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