package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.kovenant.recoverFor
import com.vfpowertech.keytap.services.AuthApiResponseException
import com.vfpowertech.keytap.services.AuthenticationService
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.UserLoginData
import com.vfpowertech.keytap.services.ui.UILoginResult
import com.vfpowertech.keytap.services.ui.UILoginService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class UILoginServiceImpl(
    private val app: KeyTapApplication,
    private val AuthenticationService: AuthenticationService
) : UILoginService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun logout() {
        app.destroyUserSession()
    }

    //this should use the keyvault is available, falling back to remote auth
    override fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception> {
        return AuthenticationService.auth(emailOrPhoneNumber, password) successUi { response ->
            //TODO need to put the username in the login response if the user used their phone number
            app.createUserSession(UserLoginData(emailOrPhoneNumber, response.keyVault, response.authToken))

            app.storeAccountData(response.keyVault)

            if (response.keyRegenCount > 0) {
                //TODO schedule prekey upload in bg
                log.info("Requested to generate {} new prekeys", response.keyRegenCount)
            }
        } map { response ->
            UILoginResult(true, null)
        } recoverFor { e: AuthApiResponseException ->
            UILoginResult(false, e.errorMessage)
        }
    }
}