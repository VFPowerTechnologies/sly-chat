package io.slychat.messenger.android

import io.slychat.messenger.services.ui.SoftKeyboardInfo
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import nl.komponents.kovenant.Promise

class AndroidUIWindowService : UIWindowService {

    override fun minimize() {}

    override fun closeSoftKeyboard() {}

    override fun copyTextToClipboard(text: String) {}

    override fun getTextFromClipboard(): String? {
        return null
    }

    override fun selectNotificationSound(previousUri: String?): Promise<UISelectionDialogResult<String?>, Exception> {
        return Promise.ofFail(Exception())
    }

    override fun setSoftKeyboardInfoListener(listener: (SoftKeyboardInfo) -> Unit) {}

    override fun clearListeners() {}

    override fun selectFileForUpload(): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }

    override fun selectSaveLocation(defaultFileName: String): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }
}