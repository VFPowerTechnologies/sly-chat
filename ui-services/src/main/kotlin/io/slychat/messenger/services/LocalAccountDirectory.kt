package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.KeyVaultPersistenceManager
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager

/**
 * Responsible for managing local account data.
 */
interface LocalAccountDirectory {
    fun findAccountFor(emailOrPhoneNumber: String): AccountInfo?

    fun findAccountFor(userId: UserId): AccountInfo?

    fun getKeyVaultManager(userId: UserId): KeyVaultPersistenceManager

    fun getSessionDataManager(userId: UserId, localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SessionDataPersistenceManager
}