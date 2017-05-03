package io.slychat.messenger.ios

import apple.foundation.NSString
import apple.mobilecoreservices.c.MobileCoreServices.kUTTypeUTF8PlainText
import apple.uikit.UIPasteboard
import io.slychat.messenger.ios.ui.WebViewController
import io.slychat.messenger.services.ui.SoftKeyboardInfo
import io.slychat.messenger.services.ui.UISelectUploadFileResult
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.moe.natj.objc.ObjCRuntime

class IOSUIWindowService(var webViewController: WebViewController?) : UIWindowService {
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

    override fun selectFileForUpload(): Promise<UISelectionDialogResult<UISelectUploadFileResult?>, Exception> {
        val vc = webViewController ?: error("No ViewController available")

        return vc.displayFileSelectMenu().map {
            UISelectionDialogResult(it != null, it)
        }
    }

    override fun selectSaveLocation(defaultFileName: String): Promise<UISelectionDialogResult<String?>, Exception> {
        TODO()
    }

    override fun setSoftKeyboardInfoListener(listener: (SoftKeyboardInfo) -> Unit) {
    }
}