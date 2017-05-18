package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.Quota

interface QuotaPersistenceManager {
    fun store(quota: Quota)

    /** Returns null if no cache file was available, or the cached file was unreadable. */
    fun retrieve(): Quota?
}
