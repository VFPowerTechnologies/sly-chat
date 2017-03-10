package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.crypto.DerivedKeyType
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.signal.SQLiteSignalProtocolStore
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.json.JsonAccountLocalInfoPersistenceManager
import io.slychat.messenger.core.persistence.sqlite.*
import io.slychat.messenger.services.LocalAccountDirectory
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.UserPaths
import org.whispersystems.libsignal.state.SignalProtocolStore

@Module
class UserPersistenceModule {
    @UserScope
    @Provides
    fun providesConversationPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): MessagePersistenceManager =
        SQLiteMessagePersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providesPreKeyPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): PreKeyPersistenceManager =
        SQLitePreKeyPersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providesContactsPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): ContactsPersistenceManager =
        SQLiteContactsPersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providersGroupPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): GroupPersistenceManager =
        SQLiteGroupPersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providersPackageQueuePersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): PackageQueuePersistenceManager =
        SQLitePackageQueuePersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providesMessageQueuePersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): MessageQueuePersistenceManager =
        SQLiteMessageQueuePersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providesSQLitePersistenceManager(
        userPaths: UserPaths,
        accountLocalInfo: AccountLocalInfo
    ): SQLitePersistenceManager {
        val sqlCipherParams = if (SlyBuildConfig.ENABLE_DATABASE_ENCRYPTION) {
            SQLCipherParams(
                accountLocalInfo.getDerivedKeySpec(LocalDerivedKeyType.SQLCIPHER),
                accountLocalInfo.sqlCipherCipher
            )
        }
        else
            null

        return SQLitePersistenceManager(userPaths.databasePath, sqlCipherParams)
    }

    //this is hacky, but we wanna expose this to the app for init/shutdown, but we don't wanna expose its type directly
    @UserScope
    @Provides
    fun providesPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): PersistenceManager = sqlitePersistenceManager

    @UserScope
    @Provides
    fun providesKeyVaultPersistenceManager(
        userData: UserData,
        localAccountDirectory: LocalAccountDirectory
    ): KeyVaultPersistenceManager {
        return localAccountDirectory.getKeyVaultPersistenceManager(userData.userId)
    }

    @UserScope
    @Provides
    fun providesSessionDataPersistenceManager(
        userLoginData: UserData,
        accountLocalInfo: AccountLocalInfo,
        localAccountDirectory: LocalAccountDirectory
    ): SessionDataPersistenceManager {
        return localAccountDirectory.getSessionDataPersistenceManager(
            userLoginData.userId,
            accountLocalInfo.getDerivedKeySpec(LocalDerivedKeyType.GENERIC)
        )
    }

    @UserScope
    @Provides
    fun providesAccountLocalInfoManager(
        keyVault: KeyVault,
        userPaths: UserPaths
    ): AccountLocalInfoPersistenceManager {
        return JsonAccountLocalInfoPersistenceManager(
            userPaths.accountParamsPath,
            keyVault.getDerivedKeySpec(DerivedKeyType.ACCOUNT_LOCAL_INFO)
        )
    }

    @UserScope
    @Provides
    fun providesAccountInfoPersistenceManager(
        userData: UserData,
        localAccountDirectory: LocalAccountDirectory
    ): AccountInfoPersistenceManager {
        return localAccountDirectory.getAccountInfoPersistenceManager(userData.userId)
    }

    @UserScope
    @Provides
    fun providesSignalSessionPersistenceManager(
        sqlitePersistenceManager: SQLitePersistenceManager
    ): SignalSessionPersistenceManager =
        SQLiteSignalSessionPersistenceManager(sqlitePersistenceManager)

    @UserScope
    @Provides
    fun providesSignalProtocolStore(
        slyApplication: SlyApplication,
        keyVault: KeyVault,
        signalSessionPersistenceManager: SignalSessionPersistenceManager,
        preKeyPersistenceManager: PreKeyPersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager
    ): SignalProtocolStore =
        SQLiteSignalProtocolStore(
            keyVault.identityKeyPair,
            slyApplication.installationData.registrationId,
            signalSessionPersistenceManager,
            preKeyPersistenceManager,
            contactsPersistenceManager
        )

    @UserScope
    @Provides
    fun providesEventLog(
        sqlitePersistenceManager: SQLitePersistenceManager
    ): EventLog {
        return SQLiteEventLog(sqlitePersistenceManager)
    }

    @UserScope
    @Provides
    fun providesFileListPersistenceManager(
        sqlitePersistenceManager: SQLitePersistenceManager
    ): FileListPersistenceManager {
        return SQLiteFileListPersistenceManager(sqlitePersistenceManager)
    }

    @UserScope
    @Provides
    fun providesUploadPersistenceManager(
        sqlitePersistenceManager: SQLitePersistenceManager
    ): UploadPersistenceManager {
        return SQLiteUploadPersistenceManager(sqlitePersistenceManager)
    }

    @UserScope
    @Provides
    fun providesDownloadPersistenceManager(
        sqlitePersistenceManager: SQLitePersistenceManager
    ): DownloadPersistenceManager {
        return SQLiteDownloadPersistenceManager(sqlitePersistenceManager)
    }
}