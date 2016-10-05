package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface AccountLocalInfoPersistenceManager {
    fun store(accountLocalInfo: AccountLocalInfo): Promise<Unit, Exception>
    fun retrieveSync(): AccountLocalInfo?
}