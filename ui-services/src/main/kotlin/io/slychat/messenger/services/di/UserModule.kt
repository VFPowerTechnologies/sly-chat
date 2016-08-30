package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.BuildConfig.ServerUrls
import io.slychat.messenger.core.crypto.EncryptionSpec
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClientImpl
import io.slychat.messenger.core.http.api.contacts.AddressBookAsyncClientImpl
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClientImpl
import io.slychat.messenger.core.http.api.offline.OfflineMessagesAsyncClientImpl
import io.slychat.messenger.core.http.api.prekeys.HttpPreKeyClient
import io.slychat.messenger.core.http.api.prekeys.PreKeyAsyncClient
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.relay.base.RelayConnector
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.auth.AuthTokenManagerImpl
import io.slychat.messenger.services.auth.TokenProvider
import io.slychat.messenger.services.auth.TokenRefresherTokenProvider
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
import rx.Observable
import rx.Scheduler
import java.util.concurrent.TimeUnit

@Module
class UserModule(
    @get:UserScope
    @get:Provides val providesUserLoginData: UserData,
    //only used during construction of AccountInfoManager; never use this directly
    private val accountInfo: AccountInfo
) {
    @UserScope
    @Provides
    fun provideRelayClientFactory(
        scheduler: Scheduler,
        relayConnector: RelayConnector,
        serverUrls: ServerUrls,
        sslConfigurator: SSLConfigurator
    ): RelayClientFactory =
        RelayClientFactoryImpl(scheduler, relayConnector, serverUrls, sslConfigurator)

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
        groupPersistenceManager: GroupPersistenceManager,
        accountInfoManager: AccountInfoManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        userLoginData: UserData,
        platformContacts: PlatformContacts,
        timerFactory: TimerFactory
    ): AddressBookSyncJobFactory {
        val serverUrl = serverUrls.API_SERVER
        val contactClient = ContactAsyncClientImpl(serverUrl, httpClientFactory)
        val contactListClient = AddressBookAsyncClientImpl(serverUrl, httpClientFactory)

        return AddressBookSyncJobFactoryImpl(
            authTokenManager,
            contactClient,
            contactListClient,
            contactsPersistenceManager,
            groupPersistenceManager,
            userLoginData,
            accountInfoManager.accountInfo,
            platformContacts,
            timerFactory
        )
    }

    @UserScope
    @Provides
    fun providesAddressBookOperationManager(
        application: SlyApplication,
        addressBookJobFactory: AddressBookSyncJobFactory,
        scheduler: Scheduler
    ): AddressBookOperationManager = AddressBookOperationManagerImpl(
        application.networkAvailable,
        addressBookJobFactory,
        DebounceScheduler(5, TimeUnit.SECONDS, scheduler)
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
        userData: UserData,
        contactsService: ContactsService,
        messagePersistenceManager: MessagePersistenceManager,
        messageCipherService: MessageCipherService,
        groupService: GroupService
    ): MessageProcessor = MessageProcessorImpl(
        userData.userId,
        contactsService,
        messagePersistenceManager,
        messageCipherService,
        groupService
    )

    @UserScope
    @Provides
    fun providesMessageReceiver(
        messageProcessor: MessageProcessor,
        packageQueuePersistenceManager: PackageQueuePersistenceManager,
        messageCipherService: MessageCipherService
    ): MessageReceiver = MessageReceiverImpl(
        messageProcessor,
        packageQueuePersistenceManager,
        messageCipherService
    )

    @UserScope
    @Provides
    fun providesMessageSender(
        relayClientManager: RelayClientManager,
        messageCipherService: MessageCipherService,
        messageQueuePersistenceManager: MessageQueuePersistenceManager
    ): MessageSender =
        MessageSenderImpl(
            messageCipherService,
            relayClientManager,
            messageQueuePersistenceManager
        )

    @UserScope
    @Provides
    fun providesMessengerService(
        contactsService: ContactsService,
        addressBookOperationManager: AddressBookOperationManager,
        messagePersistenceManager: MessagePersistenceManager,
        groupService: GroupService,
        contactsPersistenceManager: ContactsPersistenceManager,
        relayClientManager: RelayClientManager,
        messageReceiver: MessageReceiver,
        messageSender: MessageSender,
        userLoginData: UserData
    ): MessengerService =
        MessengerServiceImpl(
            contactsService,
            addressBookOperationManager,
            messagePersistenceManager,
            groupService,
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
        userConfigService: UserConfigService,
        @UIVisibility uiVisibility: Observable<Boolean>,
        scheduler: Scheduler
    ): NotifierService {
        //even if this a hot observable, it's not yet emitting so we can just connect using share() instead of
        //manually using the ConnectedObservable
        val shared = messengerService.newMessages
            //ignore messages from self
            .filter { it.info.isSent == false }
            .share()

        //we use debouncing to trigger a buffer flush
        val closingSelector = shared.debounce(400, TimeUnit.MILLISECONDS, scheduler)
        val buffered = shared.buffer(closingSelector)
        val bufferedBundles = NotifierServiceImpl.flattenMessageBundles(buffered)

        return NotifierServiceImpl(
            bufferedBundles,
            uiEventService.events,
            uiVisibility,
            contactsPersistenceManager,
            groupPersistenceManager,
            platformNotificationService,
            userConfigService
        )
    }

    @UserScope
    @Provides
    fun providesMessageCipherService(
        userData: UserData,
        authTokenManager: AuthTokenManager,
        serverUrls: ServerUrls,
        signalProtocolStore: SignalProtocolStore,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): MessageCipherService {
        val preKeyClient = HttpPreKeyClient(serverUrls.API_SERVER, httpClientFactory.create())
        return MessageCipherServiceImpl(userData.userId, authTokenManager, preKeyClient, signalProtocolStore)
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

        return PreKeyManagerImpl(
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

        return OfflineMessageManagerImpl(
            application.networkAvailable,
            offlineMessagesClient,
            messengerService,
            authTokenManager
        )
    }

    @UserScope
    @Provides
    fun providesTokenProvider(
        tokenRefresher: TokenRefresher
    ): TokenProvider =
        TokenRefresherTokenProvider(tokenRefresher)

    @UserScope
    @Provides
    fun providesAuthTokenManager(
        userLoginData: UserData,
        tokenProvider: TokenProvider,
        timerFactory: TimerFactory
    ): AuthTokenManager =
        AuthTokenManagerImpl(userLoginData.address, tokenProvider, timerFactory)

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
        addressBookOperationManager: AddressBookOperationManager
    ): GroupService =
        GroupServiceImpl(
            groupPersistenceManager,
            contactsPersistenceManager,
            addressBookOperationManager
        )

    @UserScope
    @Provides
    fun providesAccountInfoManager(
        accountInfoPersistenceManager: AccountInfoPersistenceManager
    ): AccountInfoManager {
        return AccountInfoManagerImpl(accountInfo, accountInfoPersistenceManager)
    }

    @UserScope
    @Provides
    fun providesTokenRefresher(
        application: SlyApplication,
        serverUrls: ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        userData: UserData,
        accountInfoManager: AccountInfoManager
    ): TokenRefresher {
        val loginClient = AuthenticationAsyncClientImpl(serverUrls.API_SERVER, httpClientFactory)

        return TokenRefresherImpl(
            userData,
            accountInfoManager,
            application.installationData.registrationId,
            loginClient
        )
    }

    @UserScope
    @Provides
    fun providesRelayClock(
        relayClientManager: RelayClientManager
    ): RelayClock =
        RelayClockImpl(
            relayClientManager.clockDifference,
            TimeUnit.SECONDS.toMillis(60)
        )
}
