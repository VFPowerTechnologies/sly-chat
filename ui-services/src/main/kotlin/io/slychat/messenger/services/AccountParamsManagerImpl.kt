package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountParams
import io.slychat.messenger.core.persistence.AccountParamsPersistenceManager
import nl.komponents.kovenant.Promise

class AccountParamsManagerImpl(
    private val accountParamsPersistenceManager: AccountParamsPersistenceManager
) : AccountParamsManager {

    override fun update(accountParams: AccountParams): Promise<Unit, Exception> {
        return accountParamsPersistenceManager.store(accountParams)
    }
}