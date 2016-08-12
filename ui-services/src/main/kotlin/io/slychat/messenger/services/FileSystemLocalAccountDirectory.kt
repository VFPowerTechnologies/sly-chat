package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.json.JsonAccountInfoPersistenceManager

//FIXME externalize JsonAccountInfoPersistenceManager
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
        val accountInfoFile = userPathsGenerator.getAccountInfoPath(userId)
        return JsonAccountInfoPersistenceManager(accountInfoFile).retrieveSync()
    }
}