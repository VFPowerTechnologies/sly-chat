package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.persistence.*

/**
 * Responsible for managing local account data.
 *
 * Must be thread-safe.
 */
interface LocalAccountDirectory {
    fun findAccountFor(emailOrPhoneNumber: String): AccountInfo?

    fun findAccountFor(userId: UserId): AccountInfo?

    fun getAccountInfoPersistenceManager(userId: UserId): AccountInfoPersistenceManager

    fun getKeyVaultPersistenceManager(userId: UserId): KeyVaultPersistenceManager

    fun getSessionDataPersistenceManager(userId: UserId, localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SessionDataPersistenceManager

    fun getStartupInfoPersistenceManager(): StartupInfoPersistenceManager

    /** Create user account directory structure. */
    fun createUserDirectories(userId: UserId)
}