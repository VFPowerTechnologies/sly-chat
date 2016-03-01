package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.BuildConfig.UIServiceComponent
import com.vfpowertech.keytap.core.BuildConfig.UIServiceType
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.KeyVaultPersistenceManager
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.ui.*
import com.vfpowertech.keytap.services.ui.dummy.*
import com.vfpowertech.keytap.services.ui.impl.UIContactsServiceImpl
import com.vfpowertech.keytap.services.ui.impl.UILoginServiceImpl
import com.vfpowertech.keytap.services.ui.impl.UIMessengerServiceImpl
import com.vfpowertech.keytap.services.ui.impl.UIRegistrationServiceImpl
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class UIServicesModule {
    private inline fun <R> getImplementation(component: BuildConfig.UIServiceComponent, dummy: () -> R, real: () -> R) =
        when (BuildConfig.UI_SERVICE_MAP[component]) {
            UIServiceType.DUMMY -> dummy()
            UIServiceType.REAL -> real()
        }

    @Singleton
    @Provides
    fun provideRegistrationService(
        serverUrls: BuildConfig.ServerUrls,
        accountInfoPersistenceManager: AccountInfoPersistenceManager
    ): UIRegistrationService = getImplementation(
        UIServiceComponent.REGISTRATION,
        { DummyUIRegistrationService() },
        { UIRegistrationServiceImpl(serverUrls.API_SERVER, accountInfoPersistenceManager) }
    )

    @Singleton
    @Provides
    fun provideLoginService(
        app: KeyTapApplication,
        serverUrls: BuildConfig.ServerUrls,
        keyVaultPersistenceManager: KeyVaultPersistenceManager
    ): UILoginService = getImplementation(
        UIServiceComponent.LOGIN,
        { DummyUILoginService() },
        { UILoginServiceImpl(app, serverUrls.API_SERVER, keyVaultPersistenceManager) }
    )

    @Singleton
    @Provides
    fun provideContactsService(
        app: KeyTapApplication
    ): UIContactsService = getImplementation(
        UIServiceComponent.CONTACTS,
        { DummyUIContactsService() },
        { UIContactsServiceImpl(app) }
    )

    @Singleton
    @Provides
    fun provideMessengerService(
        app: KeyTapApplication,
        contactsService: UIContactsService
    ): UIMessengerService = getImplementation(
        UIServiceComponent.MESSENGER,
        { DummyUIMessengerService(contactsService) },
        { UIMessengerServiceImpl(app) }
    )

    @Singleton
    @Provides
    fun provideHistoryService(): UIHistoryService = DummyUIHistoryService()

    @Singleton
    @Provides
    fun provideDevelService(messengerService: UIMessengerService): UIDevelService =
        UIDevelServiceImpl(
            messengerService as? DummyUIMessengerService
        )
}