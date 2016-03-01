package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.div

class UserPathsGenerator(private val platformInfo: PlatformInfo) {
    fun getPaths(username: String): UserPaths {
        val accountsDir = platformInfo.appFileStorageDirectory / "accounts" / username

        return UserPaths(
            accountsDir,
            accountsDir / "keyvault.json",
            accountsDir / "account-info.json",
            accountsDir / "db.sqlite3"
        )
    }
}