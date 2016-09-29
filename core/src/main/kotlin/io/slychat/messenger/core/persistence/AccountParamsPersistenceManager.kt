package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface AccountParamsPersistenceManager {
    fun store(accountParams: AccountParams): Promise<Unit, Exception>
    fun retrieveSync(): AccountParams?
}