package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.BuildConfig.UIServiceComponent
import com.vfpowertech.keytap.core.BuildConfig.UIServiceType
import com.vfpowertech.keytap.core.persistence.AccountInfoPersistenceManager
import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.DevelService
import com.vfpowertech.keytap.ui.services.HistoryService
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.MessengerService
import com.vfpowertech.keytap.ui.services.RegistrationService
import com.vfpowertech.keytap.ui.services.dummy.DevelServiceImpl
import com.vfpowertech.keytap.ui.services.dummy.DummyContactsService
import com.vfpowertech.keytap.ui.services.dummy.DummyHistoryService
import com.vfpowertech.keytap.ui.services.dummy.DummyLoginService
import com.vfpowertech.keytap.ui.services.dummy.DummyMessengerService
import com.vfpowertech.keytap.ui.services.dummy.DummyRegistrationService
import com.vfpowertech.keytap.ui.services.impl.LoginServiceImpl
import com.vfpowertech.keytap.ui.services.impl.RegistrationServiceImpl
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class CoreModule {
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
    fun provideLoginService(serverUrls: BuildConfig.ServerUrls): LoginService = getImplementation(
        UIServiceComponent.LOGIN,
        { DummyLoginService() },
        { LoginServiceImpl(serverUrls.API_SERVER) }
    )

    @Singleton
    @Provides
    fun provideContactsService(): ContactsService = getImplementation(
        UIServiceComponent.CONTACTS,
        { DummyContactsService() },
        { DummyContactsService() }
    )

    @Singleton
    @Provides
    fun provideMessengerService(contactsService: ContactsService): MessengerService = getImplementation(
        UIServiceComponent.MESSENGER,
        { DummyMessengerService(contactsService) },
        { DummyMessengerService(contactsService) }
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