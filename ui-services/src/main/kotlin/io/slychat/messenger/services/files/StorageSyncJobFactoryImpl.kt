package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.persistence.FileListPersistenceManager

class StorageSyncJobFactoryImpl(
    private val keyVault: KeyVault,
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val storageClient: StorageAsyncClient
) : StorageSyncJobFactory {
    override fun create(userCredentials: UserCredentials): StorageSyncJob {
        return StorageSyncJobImpl(userCredentials, keyVault, fileListPersistenceManager, storageClient)
    }
}