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
import io.slychat.messenger.core.http.api.offline.OfflineMessagesAsyncClientImpl
import io.slychat.messenger.core.http.api.prekeys.HttpPreKeyClient
import io.slychat.messenger.core.http.api.prekeys.PreKeyAsyncClient
import io.slychat.messenger.core.persistence.*
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
import io.slychat.messenger.services.contacts.*
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageCipherServiceImpl
import io.slychat.messenger.services.messaging.*
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
        RelayClientManagerImpl(scheduler, relayClientFactory)

    @UserScope
    @Provides
    fun providesContactJobFactory(
        authTokenManager: AuthTokenManager,
        serverUrls: BuildConfig.ServerUrls,
        contactsPersistenceManager: ContactsPersistenceManager,
        accountInfoPersistenceManager: AccountInfoPersistenceManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        userLoginData: UserData,
        platformContacts: PlatformContacts
    ): ContactSyncJobFactory {
        val serverUrl = serverUrls.API_SERVER
        val contactClient = ContactAsyncClientImpl(serverUrl, httpClientFactory)
        val contactListClient = ContactListAsyncClientImpl(serverUrl, httpClientFactory)

        return ContactSyncJobFactoryImpl(
            authTokenManager,
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
    fun providesAddressBookOperationManager(
        application: SlyApplication,
        contactJobFactory: ContactSyncJobFactory
    ): AddressBookOperationManager = AddressBookOperationManagerImpl(
        application.networkAvailable,
        contactJobFactory
    )

    @UserScope
    @Provides
    fun providesContactsService(
        authTokenManager: AuthTokenManager,
        serverUrls: BuildConfig.ServerUrls,
        contactsPersistenceManager: ContactsPersistenceManager,
        addressBookOperationManager: AddressBookOperationManager,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): ContactsService {
        val serverUrl = serverUrls.API_SERVER
        val contactClient = ContactAsyncClientImpl(serverUrl, httpClientFactory)

        return ContactsServiceImpl(
            authTokenManager,
            contactClient,
            contactsPersistenceManager,
            addressBookOperationManager
        )
    }

    @UserScope
    @Provides
    fun providesMessageProcessor(
        contactsService: ContactsService,
        messagePersistenceManager: MessagePersistenceManager,
        groupPersistenceManager: GroupPersistenceManager
    ): MessageProcessor = MessageProcessorImpl(
        contactsService,
        messagePersistenceManager,
        groupPersistenceManager
    )

    @UserScope
    @Provides
    fun providesMessageReceiver(
        scheduler: Scheduler,
        messageProcessor: MessageProcessor,
        packageQueuePersistenceManager: PackageQueuePersistenceManager,
        messageCipherService: MessageCipherService
    ): MessageReceiver = MessageReceiverImpl(
        scheduler,
        messageProcessor,
        packageQueuePersistenceManager,
        messageCipherService
    )

    @UserScope
    @Provides
    fun providesMessageSender(
        scheduler: Scheduler,
        relayClientManager: RelayClientManager,
        messageCipherService: MessageCipherService,
        messageQueuePersistenceManager: MessageQueuePersistenceManager
    ): MessageSender =
        MessageSenderImpl(
            scheduler,
            messageCipherService,
            relayClientManager,
            messageQueuePersistenceManager
        )

    @UserScope
    @Provides
    fun providesMessengerService(
        contactsService: ContactsService,
        messagePersistenceManager: MessagePersistenceManager,
        groupPersistenceManager: GroupPersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager,
        relayClientManager: RelayClientManager,
        messageReceiver: MessageReceiver,
        messageSender: MessageSender,
        userLoginData: UserData
    ): MessengerService =
        MessengerServiceImpl(
            contactsService,
            messagePersistenceManager,
            groupPersistenceManager,
            contactsPersistenceManager,
            relayClientManager,
            messageSender,
            messageReceiver,
            userLoginData.userId
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
        groupPersistenceManager: GroupPersistenceManager,
        platformNotificationService: PlatformNotificationService,
        userConfigService: UserConfigService
    ): NotifierService =
        NotifierService(
            messengerService.newMessages,
            uiEventService.events,
            contactsPersistenceManager,
            groupPersistenceManager,
            platformNotificationService,
            userConfigService
        )

    @UserScope
    @Provides
    fun providesMessageCipherService(
        authTokenManager: AuthTokenManager,
        serverUrls: ServerUrls,
        signalProtocolStore: SignalProtocolStore,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): MessageCipherService {
        val preKeyClient = HttpPreKeyClient(serverUrls.API_SERVER, httpClientFactory.create())
        return MessageCipherServiceImpl(authTokenManager, preKeyClient, signalProtocolStore)
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
        val offlineMessagesClient = OfflineMessagesAsyncClientImpl(serverUrl, httpClientFactory)

        return OfflineMessageManager(
            application.networkAvailable,
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

    @UserScope
    @Provides
    fun providesGroupService(
        groupPersistenceManager: GroupPersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager,
        messageProcessor: MessageProcessor
    ): GroupService =
        GroupServiceImpl(
            groupPersistenceManager,
            contactsPersistenceManager,
            messageProcessor
        )
}
