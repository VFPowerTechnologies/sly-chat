package io.slychat.messenger.desktop.services

import io.slychat.messenger.services.ui.UIPlatformService
import javafx.application.HostServices

class DesktopUIPlatformService(private val hostServices: HostServices?) : UIPlatformService {
    override fun openURL(url: String) {
        hostServices?.showDocument(url)
    }
}