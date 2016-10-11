package io.slychat.messenger.desktop.services

import io.slychat.messenger.desktop.DesktopApp
import io.slychat.messenger.services.ui.UILoadService

class DesktopUILoadService(private val app: DesktopApp) : UILoadService {
    override fun loadComplete() {
        app.uiLoadComplete()
    }
}