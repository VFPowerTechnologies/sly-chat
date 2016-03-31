package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.kovenant.recoverFor
import com.vfpowertech.keytap.core.persistence.SessionData
import com.vfpowertech.keytap.core.persistence.json.JsonSessionDataPersistenceManager
import com.vfpowertech.keytap.services.*
import com.vfpowertech.keytap.services.ui.UILoginResult
import com.vfpowertech.keytap.services.ui.UILoginService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.successUi
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
        app.destroyUserSession()
    }

    private fun localAuth(emailOrPhoneNumber: String, password: String): Promise<AuthResult, Exception> {
        return authenticationService.localAuth(emailOrPhoneNumber, password)
    }

    private fun remoteAuth(emailOrPhoneNumber: String, password: String): Promise<AuthResult, Exception> {
        //XXX technically we don't need to re-write the value on every login
        return authenticationService.remoteAuth(emailOrPhoneNumber, password)
    }

    //this should use the keyvault is available, falling back to remote auth to retrieve it
    override fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception> {
        //TODO this only works if an email is given; we need to somehow keep a list of phone numbers -> emails somewhere
        //maybe just search every account dir available and find a number that way? kinda rough but works

        //if the unlock fails, we try remotely; this can occur if the password was changed remotely from another device
        return authenticationService.auth(emailOrPhoneNumber, password) successUi { response ->
            val keyVault = response.keyVault
            //TODO need to put the username in the login response if the user used their phone number
            app.createUserSession(UserLoginData(emailOrPhoneNumber, keyVault, response.authToken), response.accountInfo)

            app.storeAccountData(keyVault, response.accountInfo)

            val paths = app.appComponent.userPathsGenerator.getPaths(emailOrPhoneNumber)
            val authToken = response.authToken
            if (authToken != null) {
                val cachedData = SessionData(authToken)
                JsonSessionDataPersistenceManager(paths.sessionDataPath, keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams).store(cachedData) fail { e ->
                    log.error("Unable to save session data to disk: {}", e.message, e)
                }
            }

            if (response.keyRegenCount > 0) {
                //TODO schedule prekey upload in bg
                log.info("Requested to generate {} new prekeys", response.keyRegenCount)
            }
        } map { response ->
            UILoginResult(true, response.accountInfo, null)
        } recoverFor { e: AuthApiResponseException ->
            UILoginResult(false, null, e.errorMessage)
        }
    }
}