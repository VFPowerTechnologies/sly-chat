package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountInfo

enum class LoginState {
    LOGGED_OUT,
    LOGGING_IN,
    LOGGED_IN,
    LOGIN_FAILED
}

interface LoginEvent {
    val state: LoginState
}

class LoggedOut : LoginEvent {
    override val state: LoginState
        get() = LoginState.LOGGED_OUT
}

class LoggingIn : LoginEvent {
    override val state: LoginState
        get() = LoginState.LOGGING_IN
}

class LoggedIn(val accountInfo: AccountInfo) : LoginEvent {
    override val state: LoginState
        get() = LoginState.LOGGED_IN
}

class LoginFailed(val errorMessage: String?, val exception: Exception?) : LoginEvent {
    init {
        require(errorMessage != null || exception != null) { "Must specify one of errorMessage or exception" }
        require(errorMessage == null || exception == null) { "Cannot specify both an error message and an exception" }
    }

    override val state: LoginState
        get() = LoginState.LOGIN_FAILED
}
