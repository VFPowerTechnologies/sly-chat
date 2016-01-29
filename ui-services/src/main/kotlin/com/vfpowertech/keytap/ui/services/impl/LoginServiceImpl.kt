package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.LoginService
import nl.komponents.kovenant.Promise

class LoginServiceImpl : LoginService {
    override fun login(emailOrPhoneNumber: String, password: String): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }
}