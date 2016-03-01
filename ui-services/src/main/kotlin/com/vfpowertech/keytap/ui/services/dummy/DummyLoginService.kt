package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.UILoginResult
import nl.komponents.kovenant.Promise

class DummyLoginService : LoginService {
    override fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception> {
        return Promise.ofSuccess(UILoginResult(true, null))
    }

    override fun logout() {
    }
}