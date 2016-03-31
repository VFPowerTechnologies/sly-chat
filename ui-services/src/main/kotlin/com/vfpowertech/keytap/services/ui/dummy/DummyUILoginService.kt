package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.LoginEvent
import com.vfpowertech.keytap.services.ui.UILoginResult
import com.vfpowertech.keytap.services.ui.UILoginService
import nl.komponents.kovenant.Promise

class DummyUILoginService : UILoginService {
    override fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception> {
        return Promise.ofSuccess(UILoginResult(true, null, null))
    }

    override fun addLoginEventListener(listener: (LoginEvent) -> Unit) {
    }

    override fun logout() {
    }
}