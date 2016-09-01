package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.BuildConfig.UIServiceComponent
import io.slychat.messenger.core.BuildConfig.UIServiceType
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.accountupdate.AccountUpdateAsyncClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClientImpl
import io.slychat.messenger.core.http.api.infoservice.InfoServiceAsyncClient
import io.slychat.messenger.core.http.api.registration.RegistrationAsyncClient
import io.slychat.messenger.services.PlatformTelephonyService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.VersionChecker
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.ui.*
import io.slychat.messenger.services.ui.dummy.UIDevelServiceImpl
import io.slychat.messenger.services.ui.impl.*
import javax.inject.Singleton

@Module
class UIServicesModule {
    private inline fun <R> getImplementation(component: BuildConfig.UIServiceComponent, dummy: () -> R, real: () -> R) =
        when (BuildConfig.UI_SERVICE_MAP[component]) {
            UIServiceType.DUMMY -> dummy()
            UIServiceType.REAL -> real()
        }

    private fun noDummyAvailable(serviceName: String): Nothing {
        error("No dummy available for $serviceName")
    }

    @Singleton
    @Provides
    fun provideRegistrationService(
        serverUrls: BuildConfig.ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): UIRegistrationService = getImplementation(
        UIServiceComponent.REGISTRATION,
        { noDummyAvailable("UIRegistrationService") },
        {
            val serverUrl = serverUrls.API_SERVER
            val registrationClient = RegistrationAsyncClient(serverUrl, httpClientFactory)
            val loginClient = AuthenticationAsyncClientImpl(serverUrl, httpClientFactory)
            UIRegistrationServiceImpl(registrationClient, loginClient)
        }
    )

    @Singleton
    @Provides
    fun provideLoginService(
        app: SlyApplication
    ): UILoginService = getImplementation(
        UIServiceComponent.LOGIN,
        { noDummyAvailable("UILoginService") },
        { UILoginServiceImpl(app) }
    )

    @Singleton
    @Provides
    fun provideContactsService(
        app: SlyApplication
    ): UIContactsService = getImplementation(
        UIServiceComponent.CONTACTS,
        { noDummyAvailable("UIContactsService") },
        { UIContactsServiceImpl(app.userSessionAvailable) }
    )

    @Singleton
    @Provides
    fun provideMessengerService(
        app: SlyApplication
    ): UIMessengerService = getImplementation(
        UIServiceComponent.MESSENGER,
        { noDummyAvailable("UIMessengerService") },
        { UIMessengerServiceImpl(app.userSessionAvailable) }
    )

    @Singleton
    @Provides
    fun provideHistoryService(): UIHistoryService = UIHistoryServiceImpl()

    @Singleton
    @Provides
    fun provideDevelService(): UIDevelService =
        UIDevelServiceImpl()

    @Singleton
    @Provides
    fun provideNetworkStatusService(app: SlyApplication): UINetworkStatusService = getImplementation(
        UIServiceComponent.NETWORK_STATUS,
        { noDummyAvailable("UINetworkStatusService") },
        { UINetworkStatusServiceImpl(app.networkAvailable, app.relayAvailable) }
    )

    @Singleton
    @Provides
    fun providesStateService(): UIStateService = UIStateServiceImpl()

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
    ): UIAccountModificationService {
        val serverUrl = serverUrls.API_SERVER
        val accountUpdateClient = AccountUpdateAsyncClient(serverUrl, httpClientFactory)
        return UIAccountModificationServiceImpl(app, accountUpdateClient)
    }

    @Singleton
    @Provides
    fun provideUIInfoService(
        @ExternalHttp httpClientFactory: HttpClientFactory
    ): UIInfoService {
        val infoServiceClient = InfoServiceAsyncClient(httpClientFactory)
        return UIInfoServiceImpl(infoServiceClient)
    }

    @Singleton
    @Provides
    fun provideUIConfigService(
       appConfigService: AppConfigService
    ): UIConfigService {
        return UIConfigServiceImpl(appConfigService)
    }

    @Singleton
    @Provides
    fun providesUIGroupService(
        app: SlyApplication
    ): UIGroupService {
        return UIGroupServiceImpl(app.userSessionAvailable)
    }

    @Singleton
    @Provides
    fun providesUIClientInfoService(
        versionChecker: VersionChecker
    ): UIClientInfoService {
        return UIClientInfoServiceImpl(versionChecker.versionOutOfDate)
    }
}