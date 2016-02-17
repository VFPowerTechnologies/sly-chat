package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLiteContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePreKeyPersistenceManager
import dagger.Module
import dagger.Provides
import java.io.File
import javax.inject.Singleton

@Module
class PersistenceModule {
    @Singleton
    @Provides
    fun providesAccountInfoPersistenceManager(platformInfo: PlatformInfo): AccountInfoPersistenceManager =
        JsonAccountInfoPersistenceManager(File(platformInfo.dataFileStorageDirectory, "account-info.json"))

    @Singleton
    @Provides
    fun providesPreKeyPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): PreKeyPersistenceManager =
        SQLitePreKeyPersistenceManager(sqlitePersistenceManager)

    @Singleton
    @Provides
    fun providesContactsPersistenceManager(sqlitePersistenceManager: SQLitePersistenceManager): ContactsPersistenceManager =
        SQLiteContactsPersistenceManager(sqlitePersistenceManager)

    //TODO I'm an idiot, need to scope this
    @Singleton
    @Provides
    fun providesSQLitePersistenceManager(platformInfo: PlatformInfo): SQLitePersistenceManager {
        val path = File(platformInfo.dataFileStorageDirectory, "db.sqlite3")
        return SQLitePersistenceManager(path, ByteArray(0), null)
    }
}