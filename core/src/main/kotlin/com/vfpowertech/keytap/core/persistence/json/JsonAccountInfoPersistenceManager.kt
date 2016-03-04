package com.vfpowertech.keytap.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonAccountInfoPersistenceManager(private val path: File) : AccountInfoPersistenceManager {
    override fun retrieve(): Promise<AccountInfo?, Exception> = task {
        if (!path.exists())
            null
        else {
            val objectMapper = ObjectMapper()
            val json = path.readText()
            if (json.length == 0)
                null
            else
                objectMapper.readValue(json, AccountInfo::class.java)
        }
    }

    override fun store(accountInfo: AccountInfo): Promise<Unit, Exception> = task {
        val objectMapper = ObjectMapper()
        val bytes = objectMapper.writeValueAsBytes(accountInfo)
        path.outputStream().write(bytes)
    }
}