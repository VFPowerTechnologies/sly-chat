package com.vfpowertech.keytap.core

import com.vfpowertech.keytap.core.persistence.SessionData
import nl.komponents.kovenant.Promise

interface SessionDataPersistenceManager {
    fun store(sessionData: SessionData): Promise<Unit, Exception>
    fun retrieve(): Promise<SessionData, Exception>
}