package io.slychat.messenger.desktop

import io.slychat.messenger.services.ui.UIShareService

class DesktopUIShareService : UIShareService {
    override fun isSupported(): Boolean {
        return false
    }

    override fun inviteToSly(subject: String, text: String, htmlText: String?) {
        throw NotImplementedError()
    }
}