package io.slychat.messenger.services

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.persistence.SessionData
import nl.komponents.kovenant.Promise

interface SessionDataManager {
    val sessionData: SessionData

    fun updateAuthToken(authToken: AuthToken?): Promise<Unit, Exception>

    //TODO
    //fun updateClockDifference(diff: Long): Promise<Unit, Exception>

    fun update(newSessionData: SessionData): Promise<Unit, Exception>

    fun delete(): Promise<Boolean, Exception>
}
