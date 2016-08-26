package io.slychat.messenger.desktop.services

import io.slychat.messenger.services.ui.UIPlatformService

class DesktopUIPlatformService(private val browser: Browser) : UIPlatformService {
    override fun openURL(url: String) {
        browser.openUrl(url)
    }
}