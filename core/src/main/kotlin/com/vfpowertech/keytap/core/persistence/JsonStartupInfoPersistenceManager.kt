package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.io.FileNotFoundException

class JsonStartupInfoPersistenceManager(val path: File) : StartupInfoPersistenceManager {
    override fun store(startupInfo: StartupInfo): Promise<Unit, Exception> = task {
        val objectMapper = ObjectMapper()
        val bytes = objectMapper.writeValueAsBytes(startupInfo)
        path.writeBytes(bytes)
    }

    override fun retrieve(): Promise<StartupInfo?, Exception> = task {
        try {
            val bytes = path.readBytes()
            val objectMapper = ObjectMapper()
            objectMapper.readValue(bytes, StartupInfo::class.java)
        }
        catch (e: FileNotFoundException) {
            null
        }
    }
}