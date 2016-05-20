package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.persistence.InstallationData
import io.slychat.messenger.core.persistence.InstallationDataPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonInstallationDataPersistenceManager(private val path: File) : InstallationDataPersistenceManager {
    override fun store(installationData: InstallationData): Promise<Unit, Exception> = task {
        writeObjectToJsonFile(path, installationData)
    }

    override fun retrieve(): Promise<InstallationData?, Exception> = task {
        readObjectFromJsonFile(path, InstallationData::class.java)
    }
}