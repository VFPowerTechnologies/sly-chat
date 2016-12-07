package io.slychat.messenger.ios

import apple.foundation.NSURL
import apple.uikit.UIApplication
import io.slychat.messenger.services.ui.UIPlatformService

class IOSUIPlatformService : UIPlatformService {
    override fun openURL(url: String) {
        UIApplication.sharedApplication().openURL(NSURL.URLWithString(url))
    }
}