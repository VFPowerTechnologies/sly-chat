package io.slychat.messenger.ios

import io.slychat.messenger.services.ui.UIShareService

class IOSUIShareService : UIShareService {
    override fun inviteToSly(subject: String, text: String, htmlText: String?) {
        TODO()
    }

    override fun isSupported(): Boolean {
        return false
    }
}