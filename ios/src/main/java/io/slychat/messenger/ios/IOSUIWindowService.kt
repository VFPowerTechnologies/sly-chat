package io.slychat.messenger.ios

import apple.foundation.NSString
import apple.mobilecoreservices.c.MobileCoreServices.kUTTypeUTF8PlainText
import apple.uikit.UIPasteboard
import io.slychat.messenger.services.ui.SoftKeyboardInfo
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import nl.komponents.kovenant.Promise
import org.moe.natj.objc.ObjCRuntime

class IOSUIWindowService : UIWindowService {
    private val utf8StringUTI: String
        get() = ObjCRuntime.cast(kUTTypeUTF8PlainText(), NSString::class.java).toString()

    override fun clearListeners() {
    }

    override fun closeSoftKeyboard() {
    }

    override fun copyTextToClipboard(text: String) {
        val pasteboard = UIPasteboard.generalPasteboard()

        pasteboard.setValueForPasteboardType(text, utf8StringUTI)
    }

    override fun getTextFromClipboard(): String? {
        val pasteboard = UIPasteboard.generalPasteboard()

        val v = pasteboard.valueForPasteboardType(utf8StringUTI) as NSString

        return v.toString()
    }

    override fun minimize() {
    }

    override fun selectNotificationSound(previousUri: String?): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }

    override fun selectFileForUpload(): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }

    override fun selectSaveLocation(defaultFileName: String): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }

    override fun setSoftKeyboardInfoListener(listener: (SoftKeyboardInfo) -> Unit) {
    }
}