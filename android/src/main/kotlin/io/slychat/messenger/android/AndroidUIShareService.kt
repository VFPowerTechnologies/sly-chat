package io.slychat.messenger.android

import io.slychat.messenger.services.ui.UIShareService

class AndroidUIShareService : UIShareService {
    override fun isSupported(): Boolean {
        return true
    }

    override fun inviteToSly(subject: String, text: String, htmlText: String?) {}
}