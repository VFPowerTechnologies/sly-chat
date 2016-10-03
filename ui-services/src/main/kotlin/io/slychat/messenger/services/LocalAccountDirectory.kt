package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.ciphers.Key
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

    fun getSessionDataPersistenceManager(userId: UserId, derivedKeySpec: DerivedKeySpec): SessionDataPersistenceManager

    fun getAccountParamsPersistenceManager(userId: UserId, derivedKeySpec: DerivedKeySpec): AccountParamsPersistenceManager

    fun getStartupInfoPersistenceManager(encryptionKey: Key?): StartupInfoPersistenceManager

    /** Create user account directory structure. */
    fun createUserDirectories(userId: UserId)
}