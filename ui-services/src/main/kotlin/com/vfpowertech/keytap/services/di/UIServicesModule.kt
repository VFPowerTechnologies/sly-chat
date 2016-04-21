package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.BuildConfig.UIServiceComponent
import com.vfpowertech.keytap.core.BuildConfig.UIServiceType
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.AuthenticationService
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.PlatformTelephonyService
import com.vfpowertech.keytap.services.ui.*
import com.vfpowertech.keytap.services.ui.dummy.*
import com.vfpowertech.keytap.services.ui.impl.*
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
        serverUrls: BuildConfig.ServerUrls
    ): UIRegistrationService = getImplementation(
        UIServiceComponent.REGISTRATION,
        { DummyUIRegistrationService() },
        { UIRegistrationServiceImpl(serverUrls.API_SERVER) }
    )

    @Singleton
    @Provides
    fun provideLoginService(
        app: KeyTapApplication,
        authenticationService: AuthenticationService
    ): UILoginService = getImplementation(
        UIServiceComponent.LOGIN,
        { DummyUILoginService() },
        { UILoginServiceImpl(app, authenticationService) }
    )

    @Singleton
    @Provides
    fun provideContactsService(
        serverUrls: BuildConfig.ServerUrls,
        app: KeyTapApplication
    ): UIContactsService = getImplementation(
        UIServiceComponent.CONTACTS,
        { DummyUIContactsService() },
        { UIContactsServiceImpl(app, serverUrls.API_SERVER) }
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
    fun provideHistoryService(): UIHistoryService = UIHistoryServiceImpl()

    @Singleton
    @Provides
    fun provideDevelService(messengerService: UIMessengerService): UIDevelService =
        UIDevelServiceImpl(
            messengerService as? DummyUIMessengerService
        )

    @Singleton
    @Provides
    fun provideNetworkStatusService(app: KeyTapApplication): UINetworkStatusService = getImplementation(
        UIServiceComponent.NETWORK_STATUS,
        { DummyUINetworkStatusService() },
        { UINetworkStatusServiceImpl(app) }
    )

    @Singleton
    @Provides
    fun providesStateService(): UIStateService = UIStateService()

    @Singleton
    @Provides
    fun providesTelephonyService(platformTelephonyService: PlatformTelephonyService): UITelephonyService =
        UITelephonyServiceImpl(platformTelephonyService)

    @Singleton
    @Provides
    fun providesUIEventService(): UIEventService =
        UIEventServiceImpl()

    @Singleton
    @Provides
    fun provideAccountModificationService(
        app: KeyTapApplication,
        serverUrls: BuildConfig.ServerUrls,
        platformInfo: PlatformInfo
    ): UIAccountModificationService = UIAccountModificationServiceImpl(app, serverUrls.API_SERVER, platformInfo)
}