package io.slychat.messenger.desktop

import io.slychat.messenger.services.ui.UIWindowService
import javafx.stage.Stage

class DesktopWindowService(private val stage: Stage) : UIWindowService {
    override fun copyTextToClipboard(text: String) {}

    override fun getTextFromClipboard(): String? {
        return null
    }

    override fun minimize() {
        stage.isIconified = true
    }

    override fun closeSoftKeyboard() {}

}