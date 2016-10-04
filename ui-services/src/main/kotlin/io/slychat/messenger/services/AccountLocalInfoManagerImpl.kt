package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountLocalInfo
import io.slychat.messenger.core.persistence.AccountLocalInfoPersistenceManager
import nl.komponents.kovenant.Promise

class AccountLocalInfoManagerImpl(
    private val accountLocalInfoPersistenceManager: AccountLocalInfoPersistenceManager
) : AccountLocalInfoManager {

    override fun update(accountLocalInfo: AccountLocalInfo): Promise<Unit, Exception> {
        return accountLocalInfoPersistenceManager.store(accountLocalInfo)
    }
}