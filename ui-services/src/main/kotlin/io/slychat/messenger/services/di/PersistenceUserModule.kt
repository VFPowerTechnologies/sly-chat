package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.sqlite.*
import io.slychat.messenger.services.LocalAccountDirectory
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.UserPaths
import io.slychat.messenger.services.crypto.SQLiteSignalProtocolStore
import org.whispersystems.libsignal.state.SignalProtocolStore

@Module
class PersistenceUserModule {
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
    fun providesSQLitePersistenceManager(userPaths: UserPaths, userLoginData: UserData): SQLitePersistenceManager {
        val keyvault = userLoginData.keyVault
        val key = if (BuildConfig.ENABLE_DATABASE_ENCRYPTION)
            keyvault.localDataEncryptionKey
        else
            null
        return SQLitePersistenceManager(userPaths.databasePath, key, keyvault.localDataEncryptionParams)
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
        localAccountDirectory: LocalAccountDirectory
    ): SessionDataPersistenceManager {
        val keyvault = userLoginData.keyVault
        return localAccountDirectory.getSessionDataPersistenceManager(
            userLoginData.userId,
            keyvault.localDataEncryptionKey,
            keyvault.localDataEncryptionParams
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
        userLoginData: UserData,
        signalSessionPersistenceManager: SignalSessionPersistenceManager,
        preKeyPersistenceManager: PreKeyPersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager
    ): SignalProtocolStore =
        SQLiteSignalProtocolStore(
            userLoginData.keyVault.identityKeyPair,
            slyApplication.installationData.registrationId,
            signalSessionPersistenceManager,
            preKeyPersistenceManager,
            contactsPersistenceManager
        )
}