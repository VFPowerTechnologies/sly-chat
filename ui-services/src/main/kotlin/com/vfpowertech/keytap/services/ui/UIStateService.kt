package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("StateService")
class UIStateService {
    private var currentState: UIState? = null

    /** Returns the current state, or null if none is set. */
    fun getState(): UIState? = currentState

    fun setState(state: UIState?) {
        currentState = state
    }
}