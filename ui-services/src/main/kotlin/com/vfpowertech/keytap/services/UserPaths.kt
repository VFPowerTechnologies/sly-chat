package com.vfpowertech.keytap.services

import java.io.File

/** Paths to user-specific data. */
data class UserPaths(
    val accountDir: File,
    val keyVaultPath: File,
    val accountInfoPath: File,
    val sessionDataPath: File,
    val databasePath: File
)