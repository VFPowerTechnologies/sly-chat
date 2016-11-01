package io.slychat.messenger.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import io.slychat.messenger.services.ui.SoftKeyboardInfo
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import nl.komponents.kovenant.Promise
import rx.Observable

class AndroidUIWindowService(
    private val context: Context,
    softKeyboardVisibility: Observable<SoftKeyboardInfo>
) : UIWindowService {
    private var softKeyboardInfo = SoftKeyboardInfo(false, 0)
    private var softKeyboardVisibilityListener: ((SoftKeyboardInfo) -> Unit)? = null

    init {
        softKeyboardVisibility.subscribe { onSoftKeyboardInfoChange(it) }
    }

    override fun minimize() {
        val androidApp = AndroidApp.get(context)

        androidApp.currentActivity?.moveTaskToBack(true)
    }

    override fun closeSoftKeyboard() {
        val androidApp = AndroidApp.get(context)

        val currentFocus = androidApp.currentActivity?.currentFocus ?: return

        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
    }

    override fun copyTextToClipboard(text: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipData = ClipData.newPlainText("", text)

        clipboardManager.primaryClip = clipData
    }

    override fun getTextFromClipboard(): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
    }

    override fun selectNotificationSound(previousUri: String?): Promise<UISelectionDialogResult<String?>, Exception> {
        val app = AndroidApp.get(context)

        val activity = app.currentActivity ?: return Promise.ofFail(IllegalStateException("No activity currently available"))

        return activity.openRingtonePicker(previousUri)
    }

    private fun onSoftKeyboardInfoChange(isVisible: SoftKeyboardInfo) {
        softKeyboardInfo = isVisible
        notifySoftKeyboardStateListener()
    }

    override fun setSoftKeyboardInfoListener(listener: (SoftKeyboardInfo) -> Unit) {
        softKeyboardVisibilityListener = listener
        notifySoftKeyboardStateListener()
    }

    private fun notifySoftKeyboardStateListener() {
        val listener = softKeyboardVisibilityListener
        if (listener != null)
            listener(softKeyboardInfo)
    }

    override fun clearListeners() {
        softKeyboardVisibilityListener = null
    }
}