package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

/** Manages persisting account info. Must not be encrypted. */
interface AccountInfoPersistenceManager {
    fun retrieveSync(): AccountInfo?
    fun retrieve(): Promise<AccountInfo?, Exception>
    fun store(accountInfo: AccountInfo): Promise<Unit, Exception>
}