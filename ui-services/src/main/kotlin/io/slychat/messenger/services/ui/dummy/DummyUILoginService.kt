package io.slychat.messenger.services.ui.dummy

import io.slychat.messenger.services.ui.UILoginService
import io.slychat.messenger.services.ui.impl.UILoginEvent

class DummyUILoginService : UILoginService {
    override fun login(emailOrPhoneNumber: String, password: String, rememberMe: Boolean) {
    }

    override fun addLoginEventListener(listener: (UILoginEvent) -> Unit) {
    }

    override fun logout() {
    }
}