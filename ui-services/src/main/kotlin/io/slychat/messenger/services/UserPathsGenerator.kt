package io.slychat.messenger.services

import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.div
import java.io.File

class UserPathsGenerator(platformInfo: PlatformInfo) {
    companion object {
        private val ACCOUNT_INFO_FILENAME = "account-info.json"
    }

    val accountsDir = platformInfo.appFileStorageDirectory / "accounts"
    val startupInfoPath = platformInfo.appFileStorageDirectory / "startup-info.json"

    /** Return the account info file path for the given user id. */
    fun getAccountInfoPath(userId: UserId): File {
        return getAccountInfoPath(accountsDir / userId.long.toString())
    }

    /** Given a path to a user's account directory, return the account info file path. */
    fun getAccountInfoPath(accountDir: File): File {
        return accountDir / ACCOUNT_INFO_FILENAME
    }

    fun getPaths(userId: UserId): UserPaths {
        val userAccountDir = accountsDir / userId.long.toString()

        return UserPaths(
            userAccountDir,
            userAccountDir / "keyvault.json",
            userAccountDir / ACCOUNT_INFO_FILENAME,
            userAccountDir / "account-params.json",
            userAccountDir / "session-data.json",
            userAccountDir / "db.sqlite3",
            userAccountDir / "user-conf.json"
        )
    }
}