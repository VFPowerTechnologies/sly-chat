package com.vfpowertech.keytap.core.persistence

import nl.komponents.kovenant.Promise

interface SessionDataPersistenceManager {
    fun store(sessionData: SessionData): Promise<Unit, Exception>
    fun retrieve(): Promise<SessionData, Exception>
}