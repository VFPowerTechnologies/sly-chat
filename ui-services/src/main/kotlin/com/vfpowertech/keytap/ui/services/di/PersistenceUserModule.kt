package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLiteContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePreKeyPersistenceManager
import com.vfpowertech.keytap.ui.services.UserLoginData
import dagger.Module
import dagger.Provides
import java.io.File

@Module
class PersistenceUserModule {
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
    fun providesSQLitePersistenceManager(platformInfo: PlatformInfo, userLoginData: UserLoginData): SQLitePersistenceManager {
        //TODO use username in path
        val path = File(platformInfo.dataFileStorageDirectory, "db.sqlite3")
        val keyvault = userLoginData.keyVault
        return SQLitePersistenceManager(path, keyvault.localDataEncryptionKey, keyvault.localDataEncryptionParams)
    }
}