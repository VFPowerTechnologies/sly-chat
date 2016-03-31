package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.persistence.AccountInfo

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
    override val state: LoginState
        get() = LoginState.LOGIN_FAILED
}
