package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("StateService")
class UIStateService {
    private var currentState: UIState? = null

    @set:Exclude
    var initialPage: String? = null
        get() {
            val v = field
            field = null
            return v
        }

    /** Returns the current state, or null if none is set. */
    fun getState(): UIState? = currentState

    fun setState(state: UIState?) {
        currentState = state
    }
}