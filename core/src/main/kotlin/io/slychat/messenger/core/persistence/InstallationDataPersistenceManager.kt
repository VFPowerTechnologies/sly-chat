package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface InstallationDataPersistenceManager {
    fun store(installationData: InstallationData): Promise<Unit, Exception>
    fun retrieve(): Promise<InstallationData?, Exception>
}