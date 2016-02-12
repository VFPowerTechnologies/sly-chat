package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.LoginResult
import com.vfpowertech.keytap.ui.services.LoginService
import nl.komponents.kovenant.Promise

class DummyLoginService : LoginService {
    override fun login(emailOrPhoneNumber: String, password: String): Promise<LoginResult, Exception> {
        return Promise.ofSuccess(LoginResult(true, null))
    }
}