package io.slychat.messenger.services

import java.io.File

/**
 * Paths to per-account data. Creation is handled by [UserPathsGenerator].
 *
 * @property accountDir Root directory for account.
 * @property keyVaultPath Path to [io.slychat.messenger.core.crypto.SerializedKeyVault].
 * @property accountInfoPath Path to [io.slychat.messenger.core.persistence.AccountInfo].
 * @property accountParamsPath Path to [io.slychat.messenger.core.persistence.AccountLocalInfo].
 * @property sessionDataPath Path to [io.slychat.messenger.core.persistence.SessionData].
 * @property databasePath Path to SQLCipher database.
 * @property configPath Path to [io.slychat.messenger.services.config.UserConfig].
 * @property attachmentCacheDir Path to directory for caching inline attachments.
 * @property quotaCachePath Path to last received [io.slychat.messenger.core.Quota].
 */
data class UserPaths(
    val accountDir: File,
    val keyVaultPath: File,
    val accountInfoPath: File,
    val accountParamsPath: File,
    val sessionDataPath: File,
    val databasePath: File,
    val configPath: File,
    val attachmentCacheDir: File,
    val quotaCachePath: File
)