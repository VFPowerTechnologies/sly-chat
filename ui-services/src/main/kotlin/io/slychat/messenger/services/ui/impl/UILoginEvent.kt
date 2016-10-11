package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.services.LoginState

sealed class UILoginEvent {
    abstract val state: LoginState

    class LoggedOut : UILoginEvent() {
        override val state: LoginState
            get() = LoginState.LOGGED_OUT
    }

    class LoggingIn : UILoginEvent() {
        override val state: LoginState
            get() = LoginState.LOGGING_IN
    }

    class LoggedIn(val accountInfo: AccountInfo, val publicKey: String) : UILoginEvent() {
        override val state: LoginState
            get() = LoginState.LOGGED_IN
    }

    class LoginFailed(val errorMessage: String) : UILoginEvent() {
        override val state: LoginState
            get() = LoginState.LOGIN_FAILED
    }
}