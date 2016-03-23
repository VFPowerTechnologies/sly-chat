package com.vfpowertech.keytap.core.persistence.json

import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonAccountInfoPersistenceManager(private val path: File) : AccountInfoPersistenceManager {
    override fun retrieve(): Promise<AccountInfo?, Exception> = task {
        readObjectFromJsonFile(path, AccountInfo::class.java)
    }

    override fun store(accountInfo: AccountInfo): Promise<Unit, Exception> = task {
        writeObjectToJsonFile(path, accountInfo)
    }
}