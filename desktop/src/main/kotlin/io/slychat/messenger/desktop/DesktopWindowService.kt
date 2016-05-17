package io.slychat.messenger.desktop

import io.slychat.messenger.services.ui.UIWindowService
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage

class DesktopWindowService(private val stage: Stage) : UIWindowService {
    override fun copyTextToClipboard(text: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }

    override fun getTextFromClipboard(): String? {
        val clipboard = Clipboard.getSystemClipboard()
        return clipboard.string
    }

    override fun minimize() {
        stage.isIconified = true
    }

    override fun closeSoftKeyboard() {}

}