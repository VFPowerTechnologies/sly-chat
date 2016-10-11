package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

/** Responsible for managing a storage medium. */
interface PersistenceManager {
    fun init()
    fun initAsync(): Promise<Unit, Exception>
    fun shutdown()
}