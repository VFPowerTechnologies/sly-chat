package io.slychat.messenger.ios

import io.slychat.messenger.services.ui.UIShareService

class IOSUIShareService : UIShareService {
    override fun inviteToSly(subject: String, text: String, htmlText: String?) {
        IOSApp.instance.inviteToSly(subject, text, htmlText)
    }

    override fun isSupported(): Boolean {
        return true
    }
}