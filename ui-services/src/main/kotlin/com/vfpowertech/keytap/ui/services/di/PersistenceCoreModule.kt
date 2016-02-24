package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.JsonKeyVaultPersistenceManager
import com.vfpowertech.keytap.core.persistence.KeyVaultPersistenceManager
import dagger.Module
import dagger.Provides
import java.io.File
import javax.inject.Singleton

@Module
class PersistenceCoreModule {
    @Singleton
    @Provides
    fun providesAccountInfoPersistenceManager(platformInfo: PlatformInfo): AccountInfoPersistenceManager =
        JsonAccountInfoPersistenceManager(File(platformInfo.dataFileStorageDirectory, "account-info.json"))

    @Singleton
    @Provides
    fun providesKeyVaultPersistenceManager(platformInfo: PlatformInfo): KeyVaultPersistenceManager {
        val path = File(platformInfo.dataFileStorageDirectory, "keyvault.json")
        return JsonKeyVaultPersistenceManager(path)
    }
}