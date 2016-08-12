package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.div
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.json.JsonAccountInfoPersistenceManager
import java.io.File

//FIXME externalize JsonAccountInfoPersistenceManager
class FileSystemLocalAccountDirectory(
    private val accountsDir: File
) : LocalAccountDirectory {
    override fun findAccountFor(emailOrPhoneNumber: String): AccountInfo? {
        val accountsDir = accountsDir

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

            val accountInfoFile = accountDir / UserPathsGenerator.ACCOUNT_INFO_FILENAME
            val accountInfo = JsonAccountInfoPersistenceManager(accountInfoFile).retrieveSync() ?: continue

            if (emailOrPhoneNumber == accountInfo.phoneNumber ||
                emailOrPhoneNumber == accountInfo.email)
                return accountInfo
        }

        return null
    }

    override fun findAccountFor(userId: UserId): AccountInfo? {
        val accountInfoFile = accountsDir / userId.toString() / UserPathsGenerator.ACCOUNT_INFO_FILENAME
        return JsonAccountInfoPersistenceManager(accountInfoFile).retrieveSync()
    }
}