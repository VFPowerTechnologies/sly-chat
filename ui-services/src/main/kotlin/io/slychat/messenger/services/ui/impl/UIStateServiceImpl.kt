package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.ui.UIState
import io.slychat.messenger.services.ui.UIStateService

class UIStateServiceImpl : UIStateService {
    private var currentState: UIState? = null

    override var initialPage: String? = null
        get() {
            val v = field
            field = null
            return v
        }

    /** Returns the current state, or null if none is set. */
    override fun getState(): UIState? = currentState

    override fun setState(state: UIState?) {
        currentState = state
    }
}