package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonAccountInfoPersistenceManager(private val path: File) : AccountInfoPersistenceManager {
    override fun retrieveSync(): AccountInfo? {
        return readObjectFromJsonFile(path, AccountInfo::class.java)
    }

    override fun retrieve(): Promise<AccountInfo?, Exception> = task {
        retrieveSync()
    }

    override fun store(accountInfo: AccountInfo): Promise<Unit, Exception> = task {
        writeObjectToJsonFile(path, accountInfo)
    }
}