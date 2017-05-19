package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.json.*
import java.io.File
import java.util.*

//FIXME externalize various persistence managers
class FileSystemLocalAccountDirectory(
    private val userPathsGenerator: UserPathsGenerator
) : LocalAccountDirectory {
    private fun getAccountDirs(): List<File> {
        val accountsDir = userPathsGenerator.accountsDir

        if (!accountsDir.exists())
            return emptyList()

        val r = ArrayList<File>()

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

            r.add(accountDir)
        }

        return r
    }

    private fun recursiveDelete(dir: File) {
        if (dir.isDirectory) {
            for (file in dir.listFiles()) {
                recursiveDelete(file)
            }
        }
        else {
            dir.delete()
        }
    }

    override fun areAccountsPresent(): Boolean {
        return getAccountDirs().isNotEmpty()
    }

    override fun findAccountFor(emailOrPhoneNumber: String): AccountInfo? {
        for (accountDir in getAccountDirs()) {
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

    override fun getAccountLocalInfoPersistenceManager(userId: UserId, derivedKeySpec: DerivedKeySpec): AccountLocalInfoPersistenceManager {
        val paths = userPathsGenerator.getPaths(userId)
        return JsonAccountLocalInfoPersistenceManager(paths.accountParamsPath, derivedKeySpec)
    }

    override fun getStartupInfoPersistenceManager(encryptionKey: Key?): StartupInfoPersistenceManager {
        val startupInfoPath = userPathsGenerator.startupInfoPath
        return JsonStartupInfoPersistenceManager(startupInfoPath, encryptionKey)
    }

    override fun createUserDirectories(userId: UserId) {
        val paths = userPathsGenerator.getPaths(userId)
        paths.accountDir.mkdirs()
        paths.attachmentCacheDir.mkdirs()
    }

    override fun deleteAccountData(userId: UserId) {
        recursiveDelete(userPathsGenerator.getPaths(userId).accountDir)
    }
}