package io.slychat.messenger.services

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import nl.komponents.kovenant.Promise
import org.slf4j.LoggerFactory

class SessionDataManagerImpl(
    private val sessionDataPersistenceManager: SessionDataPersistenceManager
) : SessionDataManager {
    private val log = LoggerFactory.getLogger(javaClass)

    override var sessionData: SessionData = SessionData()
        private set

    private fun writeSessionData(newSessionData: SessionData): Promise<Unit, Exception> {
        val oldSessionData = this.sessionData

        //don't write if nothing was changed
        if (oldSessionData == newSessionData)
            return Promise.ofSuccess(Unit)

        this.sessionData = newSessionData

        return sessionDataPersistenceManager.store(newSessionData) fail { e ->
            log.error("Unable to write session data to disk: {}", e.message, e)
        }
    }

    override fun updateAuthToken(authToken: AuthToken?): Promise<Unit, Exception> {
        val sessionData = this.sessionData

        return writeSessionData(sessionData.copy(authToken = authToken))
    }

    override fun update(newSessionData: SessionData): Promise<Unit, Exception> {
        return writeSessionData(newSessionData)
    }

    override fun delete(): Promise<Boolean, Exception> {
        sessionData = SessionData()

        return sessionDataPersistenceManager.delete() fail { e ->
            log.error("Error during session data file removal: {}", e.message, e)
        }
    }
}