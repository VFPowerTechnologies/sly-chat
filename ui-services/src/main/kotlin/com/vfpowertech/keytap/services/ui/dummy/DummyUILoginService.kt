package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.LoginEvent
import com.vfpowertech.keytap.services.ui.UILoginService

class DummyUILoginService : UILoginService {
    override fun login(emailOrPhoneNumber: String, password: String) {
    }

    override fun addLoginEventListener(listener: (LoginEvent) -> Unit) {
    }

    override fun logout() {
    }
}