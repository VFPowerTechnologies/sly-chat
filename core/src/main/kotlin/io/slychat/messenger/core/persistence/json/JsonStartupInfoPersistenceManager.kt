package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.persistence.StartupInfo
import io.slychat.messenger.core.persistence.StartupInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonStartupInfoPersistenceManager(val path: File) : StartupInfoPersistenceManager {
    override fun store(startupInfo: StartupInfo): Promise<Unit, Exception> = task {
        writeObjectToJsonFile(path, startupInfo)
    }

    override fun retrieve(): Promise<StartupInfo?, Exception> = task {
        readObjectFromJsonFile(path, StartupInfo::class.java)
    }

    override fun delete(): Promise<Unit, Exception> = task {
        path.delete()
        Unit
    }
}