package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise

interface StorageSyncJob {
    fun run(): Promise<StorageSyncResult, Exception>
}