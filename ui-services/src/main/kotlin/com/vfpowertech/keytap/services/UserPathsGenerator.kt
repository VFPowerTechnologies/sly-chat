package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.div
import java.io.File

class UserPathsGenerator(private val platformInfo: PlatformInfo) {
    companion object {
        val ACCOUNT_INFO_FILENAME = "account-info.json"
    }

    val accountsDir = platformInfo.appFileStorageDirectory / "accounts"

    fun getPaths(userId: UserId): UserPaths {
        val userAccountDir = accountsDir / userId.id.toString()

        return UserPaths(
            userAccountDir,
            userAccountDir / "keyvault.json",
            userAccountDir / ACCOUNT_INFO_FILENAME,
            userAccountDir / "session-data.json",
            userAccountDir / "db.sqlite3"
        )
    }
}