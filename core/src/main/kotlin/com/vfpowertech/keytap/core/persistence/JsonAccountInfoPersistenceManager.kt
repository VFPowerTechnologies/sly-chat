package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonAccountInfoPersistenceManager(private val path: File) : AccountInfoPersistenceManager {
    //make sure to serialize access to this
    private var accountInfo: AccountInfo? = null

    override fun retrieve(): Promise<AccountInfo?, Exception> {
        synchronized(this) {
            if (accountInfo != null)
                return Promise.of(accountInfo)
        }

        return task {
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
    }

    override fun store(accountInfo: AccountInfo): Promise<Unit, Exception> {
        synchronized(this) {
            if (accountInfo == this.accountInfo)
                return Promise.ofSuccess(Unit)
        }

        return task {
            val objectMapper = ObjectMapper()
            val bytes = objectMapper.writeValueAsBytes(accountInfo)
            path.outputStream().write(bytes)
            synchronized(this) {
                this.accountInfo = accountInfo
            }
        }
    }
}