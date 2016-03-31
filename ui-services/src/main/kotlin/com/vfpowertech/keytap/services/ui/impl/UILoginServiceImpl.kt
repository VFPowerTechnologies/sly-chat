package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.AuthResult
import com.vfpowertech.keytap.services.AuthenticationService
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.LoginEvent
import com.vfpowertech.keytap.services.ui.UILoginService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.*

/** Rejects the promise with java.io.FileNotFoundException if path doesn't exist. Otherwise resolves with the given path. */
fun asyncCheckPath(path: File): Promise<File, Exception> = task {
    if (!path.exists())
        throw FileNotFoundException()
    else
        path
}

class UILoginServiceImpl(
    private val app: KeyTapApplication,
    private val authenticationService: AuthenticationService
) : UILoginService {
    private val log = LoggerFactory.getLogger(javaClass)
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

    private fun localAuth(emailOrPhoneNumber: String, password: String): Promise<AuthResult, Exception> {
        return authenticationService.localAuth(emailOrPhoneNumber, password)
    }

    private fun remoteAuth(emailOrPhoneNumber: String, password: String): Promise<AuthResult, Exception> {
        //XXX technically we don't need to re-write the value on every login
        return authenticationService.remoteAuth(emailOrPhoneNumber, password)
    }

    //this should use the keyvault is available, falling back to remote auth to retrieve it
    override fun login(emailOrPhoneNumber: String, password: String) {
        app.login(emailOrPhoneNumber, password)
    }
}