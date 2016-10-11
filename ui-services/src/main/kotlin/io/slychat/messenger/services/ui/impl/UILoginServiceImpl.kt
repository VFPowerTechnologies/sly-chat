package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.ui.UILoginService
import java.util.*

class UILoginServiceImpl(
    private val app: SlyApplication
) : UILoginService {
    private val listeners = ArrayList<(UILoginEvent) -> Unit>()
    //cached value; is null until initialized
    private var lastLoginEvent: UILoginEvent? = null

    init {
        app.loginEvents.subscribe { updateLoginEvent(it) }
    }

    private fun notifyLoginEventListeners() {
        val ev = lastLoginEvent
        if (ev != null)
            listeners.map { it(ev) }
    }

    private fun updateLoginEvent(event: LoginEvent) {
        val uiEvent = when (event) {
            is LoginEvent.LoggedOut -> UILoginEvent.LoggedOut()
            is LoginEvent.LoggedIn -> UILoginEvent.LoggedIn(event.accountInfo, event.publicKey)
            is LoginEvent.LoggingIn -> UILoginEvent.LoggingIn()
            is LoginEvent.LoginFailed -> UILoginEvent.LoginFailed(event.errorMessage ?: event.exception?.message ?: "No error available")
        }

        lastLoginEvent = uiEvent
        notifyLoginEventListeners()
    }

    override fun addLoginEventListener(listener: (UILoginEvent) -> Unit) {
        listeners.add(listener)
        val ev = lastLoginEvent
        if (ev != null)
            listener(ev)
    }

    override fun logout() {
        app.logout()
    }

    //this should use the keyvault is available, falling back to remote auth to retrieve it
    override fun login(emailOrPhoneNumber: String, password: String, rememberMe: Boolean) {
        app.login(emailOrPhoneNumber, password, rememberMe)
    }

    override fun clearListeners() {
        listeners.clear()
    }
}