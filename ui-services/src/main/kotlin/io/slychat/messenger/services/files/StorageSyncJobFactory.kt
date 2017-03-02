package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials

interface StorageSyncJobFactory {
    fun create(userCredentials: UserCredentials): StorageSyncJob
}