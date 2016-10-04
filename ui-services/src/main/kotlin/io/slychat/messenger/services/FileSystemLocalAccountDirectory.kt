package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.json.*

//FIXME externalize various persistence managers
class FileSystemLocalAccountDirectory(
    private val userPathsGenerator: UserPathsGenerator
) : LocalAccountDirectory {
    override fun findAccountFor(emailOrPhoneNumber: String): AccountInfo? {
        val accountsDir = userPathsGenerator.accountsDir

        if (!accountsDir.exists())
            return null

        for (accountDir in accountsDir.listFiles()) {
            if (!accountDir.isDirectory)
                continue

            //ignore non-numeric dirs
            try {
                accountDir.name.toLong()
            }
            catch (e: NumberFormatException) {
                continue
            }

            val accountInfoFile = userPathsGenerator.getAccountInfoPath(accountDir)
            val accountInfo = JsonAccountInfoPersistenceManager(accountInfoFile).retrieveSync() ?: continue

            if (emailOrPhoneNumber == accountInfo.phoneNumber ||
                emailOrPhoneNumber == accountInfo.email)
                return accountInfo
        }

        return null
    }

    override fun findAccountFor(userId: UserId): AccountInfo? {
        return getAccountInfoPersistenceManager(userId).retrieveSync()
    }

    override fun getAccountInfoPersistenceManager(userId: UserId): AccountInfoPersistenceManager {
        val accountInfoFile = userPathsGenerator.getAccountInfoPath(userId)
        return JsonAccountInfoPersistenceManager(accountInfoFile)
    }

    override fun getKeyVaultPersistenceManager(userId: UserId): KeyVaultPersistenceManager {
        val paths = userPathsGenerator.getPaths(userId)
        return JsonKeyVaultPersistenceManager(paths.keyVaultPath)
    }

    override fun getSessionDataPersistenceManager(userId: UserId, derivedKeySpec: DerivedKeySpec): SessionDataPersistenceManager {
        val paths = userPathsGenerator.getPaths(userId)
        return JsonSessionDataPersistenceManager(paths.sessionDataPath, derivedKeySpec)
    }

    override fun getAccountParamsPersistenceManager(userId: UserId, derivedKeySpec: DerivedKeySpec): AccountLocalInfoPersistenceManager {
        val paths = userPathsGenerator.getPaths(userId)
        return JsonAccountLocalInfoPersistenceManager(paths.accountParamsPath, derivedKeySpec)
    }

    override fun getStartupInfoPersistenceManager(encryptionKey: Key?): StartupInfoPersistenceManager {
        val startupInfoPath = userPathsGenerator.startupInfoPath
        return JsonStartupInfoPersistenceManager(startupInfoPath, encryptionKey)
    }

    override fun createUserDirectories(userId: UserId) {
        userPathsGenerator.getPaths(userId).accountDir.mkdirs()
    }
}