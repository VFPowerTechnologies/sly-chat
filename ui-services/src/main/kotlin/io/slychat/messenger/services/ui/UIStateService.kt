package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("StateService")
interface UIStateService {
    val initialPage: String?

    /** Returns the current state, or null if none is set. */
    fun getState(): UIState?

    fun setState(state: UIState?)
}
