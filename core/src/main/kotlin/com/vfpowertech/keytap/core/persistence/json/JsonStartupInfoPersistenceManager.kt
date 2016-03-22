package com.vfpowertech.keytap.core.persistence.json

import com.vfpowertech.keytap.core.persistence.StartupInfo
import com.vfpowertech.keytap.core.persistence.StartupInfoPersistenceManager
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
}