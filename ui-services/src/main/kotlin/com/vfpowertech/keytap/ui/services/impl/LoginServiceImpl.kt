package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.ui.services.LoginService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

//XXX just throw on errors, since we designed this badly
class LoginServiceImpl : LoginService {
    val logger = LoggerFactory.getLogger(javaClass)
    override fun login(emailOrPhoneNumber: String, password: String): Promise<Unit, Exception> {
        val loginClient = AuthenticationClientWrapper()
        return loginClient.getParams(emailOrPhoneNumber) bind { authParams ->
            val hashParams = HashDeserializers.deserialize(authParams.hashParams)
            val hash = hashPasswordWithParams(password, hashParams)

            val request = AuthenticationRequest(emailOrPhoneNumber, hash.hexify(), authParams.csrfToken)
            loginClient.auth(request) successUi { response ->
                CredentialsManager.authToken = response.authToken!!
            } map { result ->
                if (result.keyRegen != null) {
                    //TODO schedule prekey upload in bg
                    logger.info("Requested to generate {} new prekeys", result.keyRegen)
                }
                Unit
            }
        }
    }
}