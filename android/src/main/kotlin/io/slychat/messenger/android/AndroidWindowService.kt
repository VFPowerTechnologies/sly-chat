package io.slychat.messenger.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import io.slychat.messenger.services.ui.UIWindowService

class AndroidWindowService(private val context: Context) : UIWindowService {
    override fun minimize() {
        val androidApp = AndroidApp.get(context)

        val activity = androidApp.currentActivity ?: return
        activity.moveTaskToBack(true)
    }

    override fun closeSoftKeyboard() {
        val androidApp = AndroidApp.get(context)

        val currentFocus = androidApp.currentActivity?.currentFocus

        if(currentFocus != null) {
            val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
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
}