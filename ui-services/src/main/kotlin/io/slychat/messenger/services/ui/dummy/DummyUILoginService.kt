package io.slychat.messenger.services.ui.dummy

import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.ui.UILoginService

class DummyUILoginService : UILoginService {
    override fun login(emailOrPhoneNumber: String, password: String, rememberMe: Boolean) {
    }

    override fun addLoginEventListener(listener: (LoginEvent) -> Unit) {
    }

    override fun logout() {
    }
}