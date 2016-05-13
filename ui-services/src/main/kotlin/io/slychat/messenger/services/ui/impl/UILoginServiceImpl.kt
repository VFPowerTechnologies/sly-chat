package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.ui.UILoginService
import java.util.*

class UILoginServiceImpl(
    private val app: SlyApplication
) : UILoginService {
    private val listeners = ArrayList<(LoginEvent) -> Unit>()
    //cached value; is null until initialized
    private var lastLoginEvent: LoginEvent? = null

    init {
        app.loginEvents.subscribe { updateLoginEvent(it) }
    }

    private fun notifyLoginEventListeners() {
        val ev = lastLoginEvent
        if (ev != null)
            listeners.map { it(ev) }
    }

    private fun updateLoginEvent(event: LoginEvent) {
        lastLoginEvent = event
        notifyLoginEventListeners()
    }

    override fun addLoginEventListener(listener: (LoginEvent) -> Unit) {
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
}