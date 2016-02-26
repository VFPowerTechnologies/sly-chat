package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.BuildConfig.UIServiceComponent
import com.vfpowertech.keytap.core.BuildConfig.UIServiceType
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.KeyVaultPersistenceManager
import com.vfpowertech.keytap.ui.services.*
import com.vfpowertech.keytap.ui.services.dummy.*
import com.vfpowertech.keytap.ui.services.impl.ContactsServiceImpl
import com.vfpowertech.keytap.ui.services.impl.LoginServiceImpl
import com.vfpowertech.keytap.ui.services.impl.MessengerServiceImpl
import com.vfpowertech.keytap.ui.services.impl.RegistrationServiceImpl
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
    ): RegistrationService = getImplementation(
        UIServiceComponent.REGISTRATION,
        { DummyRegistrationService() },
        { RegistrationServiceImpl(serverUrls.API_SERVER, accountInfoPersistenceManager) }
    )

    @Singleton
    @Provides
    fun provideLoginService(
        app: KeyTapApplication,
        serverUrls: BuildConfig.ServerUrls,
        keyVaultPersistenceManager: KeyVaultPersistenceManager
    ): LoginService = getImplementation(
        UIServiceComponent.LOGIN,
        { DummyLoginService() },
        { LoginServiceImpl(app, serverUrls.API_SERVER, keyVaultPersistenceManager) }
    )

    @Singleton
    @Provides
    fun provideContactsService(
        app: KeyTapApplication
    ): ContactsService = getImplementation(
        UIServiceComponent.CONTACTS,
        { DummyContactsService() },
        { ContactsServiceImpl(app) }
    )

    //FIXME
    @Singleton
    @Provides
    fun provideMessengerService(
        app: KeyTapApplication,
        contactsService: ContactsService
    ): MessengerService = getImplementation(
        UIServiceComponent.MESSENGER,
        { DummyMessengerService(contactsService) },
        { MessengerServiceImpl(app) }
    )

    @Singleton
    @Provides
    fun provideHistoryService(): HistoryService = DummyHistoryService()

    @Singleton
    @Provides
    fun provideDevelService(messengerService: MessengerService): DevelService =
        DevelServiceImpl(
            messengerService as? DummyMessengerService
        )
}