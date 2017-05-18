package io.slychat.messenger.services

import java.io.File

/** Paths to user-specific data. */
data class UserPaths(
    val accountDir: File,
    val keyVaultPath: File,
    val accountInfoPath: File,
    val accountParamsPath: File,
    val sessionDataPath: File,
    val databasePath: File,
    val configPath: File,
    val fileCacheDir: File,
    val quotaCachePath: File
)