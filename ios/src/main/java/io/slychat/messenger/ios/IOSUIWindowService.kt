package io.slychat.messenger.ios

import io.slychat.messenger.services.ui.SoftKeyboardInfo
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import nl.komponents.kovenant.Promise

class IOSUIWindowService : UIWindowService {
    override fun clearListeners() {
    }

    override fun closeSoftKeyboard() {
    }

    override fun copyTextToClipboard(text: String) {
    }

    override fun getTextFromClipboard(): String? {
        return null
    }

    override fun minimize() {
    }

    override fun selectNotificationSound(previousUri: String?): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }

    override fun setSoftKeyboardInfoListener(listener: (SoftKeyboardInfo) -> Unit) {
    }
}