package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.LoginService
import com.vfpowertech.keytap.services.ui.UILoginResult
import nl.komponents.kovenant.Promise

class DummyLoginService : LoginService {
    override fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception> {
        return Promise.ofSuccess(UILoginResult(true, null))
    }

    override fun logout() {
    }
}