package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.accountupdate.AccountUpdateAsyncClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClientImpl
import io.slychat.messenger.core.http.api.availability.AvailabilityAsyncClientImpl
import io.slychat.messenger.core.http.api.feedback.FeedbackAsyncClientImpl
import io.slychat.messenger.core.http.api.infoservice.InfoServiceAsyncClient
import io.slychat.messenger.core.http.api.registration.RegistrationAsyncClient
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.PlatformTelephonyService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.VersionChecker
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.ui.*
import io.slychat.messenger.services.ui.impl.*
import javax.inject.Singleton

@Module
class UIServicesModule {
    @Singleton
    @Provides
    fun provideRegistrationService(
        serverUrls: BuildConfig.ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): UIRegistrationService {
            val serverUrl = serverUrls.API_SERVER
            val registrationClient = RegistrationAsyncClient(serverUrl, httpClientFactory)
            val loginClient = AuthenticationAsyncClientImpl(serverUrl, httpClientFactory)
            val availabilityClient = AvailabilityAsyncClientImpl(serverUrl, httpClientFactory)
            return UIRegistrationServiceImpl(registrationClient, loginClient, availabilityClient)
    }

    @Singleton
    @Provides
    fun provideLoginService(
        app: SlyApplication
    ): UILoginService {
        return UILoginServiceImpl(app)
    }

    @Singleton
    @Provides
    fun provideContactsService(
        app: SlyApplication
    ): UIContactsService {
        return UIContactsServiceImpl(app.userSessionAvailable)
    }

    @Singleton
    @Provides
    fun provideMessengerService(
        app: SlyApplication
    ): UIMessengerService {
        return UIMessengerServiceImpl(app.userSessionAvailable)
    }

    @Singleton
    @Provides
    fun provideHistoryService(): UIHistoryService = UIHistoryServiceImpl()

    @Singleton
    @Provides
    fun provideNetworkStatusService(app: SlyApplication): UINetworkStatusService {
        return UINetworkStatusServiceImpl(app.networkAvailable, app.relayAvailable)
    }

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
        app: SlyApplication,
        appConfigService: AppConfigService,
        platformNotificationService: PlatformNotificationService
    ): UIConfigService {
        return UIConfigServiceImpl(
            app.userSessionAvailable,
            appConfigService,
            platformNotificationService
        )
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

    @Singleton
    @Provides
    fun providesUIFeedbackService(
        app: SlyApplication,
        serverUrls: BuildConfig.ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): UIFeedbackService {
        val feedbackClient = FeedbackAsyncClientImpl(serverUrls.API_SERVER, httpClientFactory)
        return UIFeedbackServiceImpl(app.userSessionAvailable, feedbackClient)
    }
}