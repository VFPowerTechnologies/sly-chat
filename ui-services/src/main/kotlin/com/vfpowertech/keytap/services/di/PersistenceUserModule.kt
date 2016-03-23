package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.persistence.SessionDataPersistenceManager
import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonKeyVaultPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonSessionDataPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLiteContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLiteMessagePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePreKeyPersistenceManager
import com.vfpowertech.keytap.services.UserLoginData
import com.vfpowertech.keytap.services.UserPaths
import dagger.Module
import dagger.Provides

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
    fun providesSQLitePersistenceManager(userPaths: UserPaths, userLoginData: UserLoginData): SQLitePersistenceManager {
        val keyvault = userLoginData.keyVault
        return SQLitePersistenceManager(userPaths.databasePath, keyvault.localDataEncryptionKey, keyvault.localDataEncryptionParams)
    }

    @UserScope
    @Provides
    fun providesKeyVaultPersistenceManager(userPaths: UserPaths): KeyVaultPersistenceManager {
        return JsonKeyVaultPersistenceManager(userPaths.keyVaultPath)
    }

    @UserScope
    @Provides
    fun providesSessionDataPersistenceManager(userPaths: UserPaths, userLoginData: UserLoginData): SessionDataPersistenceManager {
        val keyvault = userLoginData.keyVault
        return JsonSessionDataPersistenceManager(userPaths.sessionDataPath, keyvault.localDataEncryptionKey, keyvault.localDataEncryptionParams)
    }

    @UserScope
    @Provides
    fun providesAccountInfoPersistenceManager(userPaths: UserPaths): AccountInfoPersistenceManager =
        JsonAccountInfoPersistenceManager(userPaths.accountInfoPath)
}