package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.JsonAccountInfoPersistenceManager
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
}