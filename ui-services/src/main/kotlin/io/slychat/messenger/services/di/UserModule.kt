package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.BuildConfig.ServerUrls
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.PreKeyPersistenceManager
import io.slychat.messenger.core.relay.base.RelayConnector
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.auth.AuthenticationServiceTokenProvider
import io.slychat.messenger.services.auth.TokenProvider
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.ui.UIEventService
import org.whispersystems.libsignal.state.SignalProtocolStore
import rx.Scheduler

@Module
class UserModule(
    @get:UserScope
    @get:Provides
    val providesUserLoginData: UserData
) {
    @UserScope
    @Provides
    fun provideRelayClientFactory(
        scheduler: Scheduler,
        relayConnector: RelayConnector,
        serverUrls: ServerUrls
    ): RelayClientFactory=
        RelayClientFactory(scheduler, relayConnector, serverUrls)

    @UserScope
    @Provides
    fun providesRelayClientManager(
        scheduler: Scheduler,
        relayClientFactory: RelayClientFactory
    ): RelayClientManager =
        RelayClientManager(scheduler, relayClientFactory)

    @UserScope
    @Provides
    fun providesContactsService(
        authTokenManager: AuthTokenManager,
        serverUrls: BuildConfig.ServerUrls,
        application: SlyApplication,
        contactsPersistenceManager: ContactsPersistenceManager,
        userLoginData: UserData,
        accountInfoPersistenceManager: AccountInfoPersistenceManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        platformContacts: PlatformContacts
    ): ContactsService =
        ContactsService(
            authTokenManager,
            serverUrls.API_SERVER,
            application,
            httpClientFactory,
            contactsPersistenceManager,
            userLoginData,
            accountInfoPersistenceManager,
            platformContacts
        )

    @UserScope
    @Provides
    fun providesMessengerService(
        application: SlyApplication,
        scheduler: Scheduler,
        contactsService: ContactsService,
        messagePersistenceManager: MessagePersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager,
        relayClientManager: RelayClientManager,
        messageCipherService: MessageCipherService,
        userLoginData: UserData
    ): MessengerService =
        MessengerService(
            application,
            scheduler,
            contactsService,
            messagePersistenceManager,
            contactsPersistenceManager,
            relayClientManager,
            messageCipherService,
            userLoginData
        )

    @UserScope
    @Provides
    fun providersUserPaths(
        userLoginData: UserData,
        userPathsGenerator: UserPathsGenerator
    ): UserPaths =
        userPathsGenerator.getPaths(userLoginData.userId)

    @UserScope
    @Provides
    fun providesNotifierService(
        messengerService: MessengerService,
        uiEventService: UIEventService,
        contactsPersistenceManager: ContactsPersistenceManager,
        platformNotificationService: PlatformNotificationService
    ): NotifierService =
        NotifierService(messengerService, uiEventService, contactsPersistenceManager, platformNotificationService)

    @UserScope
    @Provides
    fun providesMessageCipherService(
        authTokenManager: AuthTokenManager,
        serverUrls: ServerUrls,
        signalProtocolStore: SignalProtocolStore,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): MessageCipherService =
        MessageCipherService(authTokenManager, httpClientFactory, signalProtocolStore, serverUrls)

    @UserScope
    @Provides
    fun providesPreKeyManager(
        application: SlyApplication,
        serverUrls: ServerUrls,
        userLoginData: UserData,
        preKeyPersistenceManager: PreKeyPersistenceManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager
    ): PreKeyManager =
        PreKeyManager(application, serverUrls.API_SERVER, httpClientFactory, userLoginData, preKeyPersistenceManager, authTokenManager)

    @UserScope
    @Provides
    fun providesOfflineMessageManager(
        application: SlyApplication,
        serverUrls: ServerUrls,
        messengerService: MessengerService,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager
    ): OfflineMessageManager =
        OfflineMessageManager(application, serverUrls.API_SERVER, httpClientFactory, messengerService, authTokenManager)

    @UserScope
    @Provides
    fun providesTokenProvider(
        application: SlyApplication,
        userLoginData: UserData,
        authenticationService: AuthenticationService
    ): TokenProvider =
        AuthenticationServiceTokenProvider(
            application,
            userLoginData,
            authenticationService
        )

    @UserScope
    @Provides
    fun providesAuthTokenManager(
        userLoginData: UserData,
        tokenProvider: TokenProvider
    ): AuthTokenManager =
        AuthTokenManager(userLoginData.address, tokenProvider)
}
