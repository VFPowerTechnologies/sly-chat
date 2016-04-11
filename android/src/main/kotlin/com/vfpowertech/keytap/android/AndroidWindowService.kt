package com.vfpowertech.keytap.android

import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.vfpowertech.keytap.services.ui.UIWindowService
import nl.komponents.kovenant.Promise

class AndroidWindowService(private val context: Context) : UIWindowService {
    override fun minimize() {
        val androidApp = AndroidApp.get(context)

        val activity = androidApp.currentActivity ?: return
        activity.moveTaskToBack(true)
    }

    override fun closeSoftKeyboard(): Promise<Unit, Exception> {
        val androidApp = AndroidApp.get(context)

        val currentFocus = androidApp.currentActivity?.currentFocus

        if(currentFocus != null) {
            val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }

        return Promise.ofSuccess(Unit)
    }
}