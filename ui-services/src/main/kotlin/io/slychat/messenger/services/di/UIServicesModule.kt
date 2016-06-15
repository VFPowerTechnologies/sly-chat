package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.BuildConfig.UIServiceComponent
import io.slychat.messenger.core.BuildConfig.UIServiceType
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.services.PlatformTelephonyService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.ui.*
import io.slychat.messenger.services.ui.dummy.*
import io.slychat.messenger.services.ui.impl.*
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
        @SlyHttp httpClientFactory: HttpClientFactory
    ): UIRegistrationService = getImplementation(
        UIServiceComponent.REGISTRATION,
        { DummyUIRegistrationService() },
        { UIRegistrationServiceImpl(serverUrls.API_SERVER, httpClientFactory) }
    )

    @Singleton
    @Provides
    fun provideLoginService(
        app: SlyApplication
    ): UILoginService = getImplementation(
        UIServiceComponent.LOGIN,
        { DummyUILoginService() },
        { UILoginServiceImpl(app) }
    )

    @Singleton
    @Provides
    fun provideContactsService(
        serverUrls: BuildConfig.ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        app: SlyApplication
    ): UIContactsService = getImplementation(
        UIServiceComponent.CONTACTS,
        { DummyUIContactsService() },
        { UIContactsServiceImpl(app, serverUrls.API_SERVER, httpClientFactory) }
    )

    @Singleton
    @Provides
    fun provideMessengerService(
        app: SlyApplication,
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
    fun provideNetworkStatusService(app: SlyApplication): UINetworkStatusService = getImplementation(
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
        app: SlyApplication,
        @SlyHttp httpClientFactory: HttpClientFactory,
        serverUrls: BuildConfig.ServerUrls
    ): UIAccountModificationService = UIAccountModificationServiceImpl(app, httpClientFactory, serverUrls.API_SERVER)

    @Singleton
    @Provides
    fun provideUIInfoService(
        @ExternalHttp httpClientFactory: HttpClientFactory
    ): UIInfoService = UIInfoServiceImpl(httpClientFactory)
}