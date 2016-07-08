package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.BuildConfig.ServerUrls
import io.slychat.messenger.core.crypto.EncryptionSpec
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClientImpl
import io.slychat.messenger.core.http.api.contacts.ContactListAsyncClientImpl
import io.slychat.messenger.core.http.api.offline.OfflineMessagesAsyncClient
import io.slychat.messenger.core.http.api.prekeys.HttpPreKeyClient
import io.slychat.messenger.core.http.api.prekeys.PreKeyAsyncClient
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.PreKeyPersistenceManager
import io.slychat.messenger.core.relay.base.RelayConnector
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.auth.AuthTokenManagerImpl
import io.slychat.messenger.services.auth.AuthenticationServiceTokenProvider
import io.slychat.messenger.services.auth.TokenProvider
import io.slychat.messenger.services.config.CipherConfigStorageFilter
import io.slychat.messenger.services.config.FileConfigStorage
import io.slychat.messenger.services.config.JsonConfigBackend
import io.slychat.messenger.services.config.UserConfigService
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
        serverUrls: ServerUrls,
        sslConfigurator: SSLConfigurator
    ): RelayClientFactory =
        RelayClientFactory(scheduler, relayConnector, serverUrls, sslConfigurator)

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
    ): ContactsService {
        val serverUrl = serverUrls.API_SERVER
        val contactClient = ContactAsyncClientImpl(serverUrl, httpClientFactory)
        val contactListClient = ContactListAsyncClientImpl(serverUrl, httpClientFactory)

        return ContactsService(
            authTokenManager,
            application.networkAvailable,
            contactClient,
            contactListClient,
            contactsPersistenceManager,
            userLoginData,
            accountInfoPersistenceManager,
            platformContacts
        )
    }

    @UserScope
    @Provides
    fun providesMessengerService(
        scheduler: Scheduler,
        contactsService: ContactsService,
        messagePersistenceManager: MessagePersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager,
        relayClientManager: RelayClientManager,
        messageCipherService: MessageCipherService,
        userLoginData: UserData
    ): MessengerService =
        MessengerServiceImpl(
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
        platformNotificationService: PlatformNotificationService,
        userConfigService: UserConfigService
    ): NotifierService =
        NotifierService(messengerService, uiEventService, contactsPersistenceManager, platformNotificationService, userConfigService)

    @UserScope
    @Provides
    fun providesMessageCipherService(
        authTokenManager: AuthTokenManager,
        serverUrls: ServerUrls,
        signalProtocolStore: SignalProtocolStore,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): MessageCipherService {
        val preKeyClient = HttpPreKeyClient(serverUrls.API_SERVER, httpClientFactory.create())
        return MessageCipherService(authTokenManager, preKeyClient, signalProtocolStore)
    }

    @UserScope
    @Provides
    fun providesPreKeyManager(
        application: SlyApplication,
        serverUrls: ServerUrls,
        userLoginData: UserData,
        preKeyPersistenceManager: PreKeyPersistenceManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager
    ): PreKeyManager {
        val serverUrl = serverUrls.API_SERVER
        val preKeyAsyncClient = PreKeyAsyncClient(serverUrl, httpClientFactory)

        return PreKeyManager(
            application,
            userLoginData,
            preKeyAsyncClient,
            preKeyPersistenceManager,
            authTokenManager
        )
    }

    @UserScope
    @Provides
    fun providesOfflineMessageManager(
        application: SlyApplication,
        serverUrls: ServerUrls,
        messengerService: MessengerService,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager
    ): OfflineMessageManager {
        val serverUrl = serverUrls.API_SERVER
        val offlineMessagesClient = OfflineMessagesAsyncClient(serverUrl, httpClientFactory)

        return OfflineMessageManager(
            application,
            offlineMessagesClient,
            messengerService,
            authTokenManager
        )
    }

    @UserScope
    @Provides
    fun providesTokenProvider(
        application: SlyApplication,
        userLoginData: UserData,
        authenticationService: AuthenticationService
    ): TokenProvider =
        AuthenticationServiceTokenProvider(
            application.installationData.registrationId,
            userLoginData,
            authenticationService
        )

    @UserScope
    @Provides
    fun providesAuthTokenManager(
        userLoginData: UserData,
        tokenProvider: TokenProvider
    ): AuthTokenManager =
        AuthTokenManagerImpl(userLoginData.address, tokenProvider)

    @UserScope
    @Provides
    fun providesConfigService(
        userLoginData: UserData,
        userPaths: UserPaths
    ): UserConfigService {
        val fileStorage = FileConfigStorage(userPaths.configPath)
        val storage = if (BuildConfig.ENABLE_CONFIG_ENCRYPTION) {
            val keyVault = userLoginData.keyVault
            val key = keyVault.localDataEncryptionKey
            val params = keyVault.localDataEncryptionParams
            val spec = EncryptionSpec(key, params)
            //can't use Cipher*Stream since we're using bouncycastle to properly support stuff
            CipherConfigStorageFilter(spec, fileStorage)
        }
        else
            fileStorage

        val backend = JsonConfigBackend("user-config", storage)
        return UserConfigService(backend)
    }
}
