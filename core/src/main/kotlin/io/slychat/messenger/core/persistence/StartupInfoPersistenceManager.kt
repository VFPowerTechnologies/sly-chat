package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface StartupInfoPersistenceManager {
    fun store(startupInfo: StartupInfo): Promise<Unit, Exception>

    /** Should return null if no info is currently saved. */
    fun retrieve(): Promise<StartupInfo?, Exception>
}
