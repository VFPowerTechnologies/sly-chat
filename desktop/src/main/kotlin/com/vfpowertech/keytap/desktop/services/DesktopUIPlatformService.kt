package com.vfpowertech.keytap.desktop.services

import com.vfpowertech.keytap.services.ui.UIPlatformService
import javafx.application.HostServices

class DesktopUIPlatformService(private val hostServices: HostServices?) : UIPlatformService {
    override fun openURL(url: String) {
        hostServices?.showDocument(url)
    }
}