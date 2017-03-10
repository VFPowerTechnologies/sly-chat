package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.SlyBuildConfig.ServerUrls
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClientImpl
import io.slychat.messenger.core.http.api.contacts.AddressBookAsyncClientImpl
import io.slychat.messenger.core.http.api.contacts.ContactLookupAsyncClientImpl
import io.slychat.messenger.core.http.api.offline.OfflineMessagesAsyncClientImpl
import io.slychat.messenger.core.http.api.prekeys.HttpPreKeyClient
import io.slychat.messenger.core.http.api.prekeys.PreKeyAsyncClient
import io.slychat.messenger.core.http.api.storage.StorageAsyncClientImpl
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.relay.base.RelayConnector
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.auth.AuthTokenManagerImpl
import io.slychat.messenger.services.auth.TokenProvider
import io.slychat.messenger.services.auth.TokenRefresherTokenProvider
import io.slychat.messenger.services.config.*
import io.slychat.messenger.services.contacts.*
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageCipherServiceImpl
import io.slychat.messenger.services.di.annotations.EmptyReadMessageQueue
import io.slychat.messenger.services.di.annotations.NetworkStatus
import io.slychat.messenger.services.di.annotations.SlyHttp
import io.slychat.messenger.services.di.annotations.UIVisibility
import io.slychat.messenger.services.files.*
import io.slychat.messenger.services.messaging.*
import io.slychat.messenger.services.ui.UIEventService
import org.whispersystems.libsignal.state.SignalProtocolStore
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

@Module
class UserModule(
    @get:UserScope
    @get:Provides
    val providesUserLoginData: UserData,

    @get:UserScope
    @get:Provides
    val providesKeyVault: KeyVault,

    //only used during construction of AccountInfoManager; never use this directly
    private val accountInfo: AccountInfo,

    @get:UserScope
    @get:Provides
    val providesAccountLocalInfo: AccountLocalInfo
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
        serverUrls: SlyBuildConfig.ServerUrls,
        contactsPersistenceManager: ContactsPersistenceManager,
        groupPersistenceManager: GroupPersistenceManager,
        accountInfoManager: AccountInfoManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        keyVault: KeyVault,
        platformContacts: PlatformContacts,
        promiseTimerFactory: PromiseTimerFactory
    ): AddressBookSyncJobFactory {
        val serverUrl = serverUrls.API_SERVER
        val contactClient = ContactLookupAsyncClientImpl(serverUrl, httpClientFactory)
        val contactListClient = AddressBookAsyncClientImpl(serverUrl, httpClientFactory)

        return AddressBookSyncJobFactoryImpl(
            authTokenManager,
            contactClient,
            contactListClient,
            contactsPersistenceManager,
            groupPersistenceManager,
            keyVault,
            accountInfoManager.accountInfo,
            platformContacts,
            promiseTimerFactory
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
        DebounceSyncScheduler(5, TimeUnit.SECONDS, scheduler)
    )

    @UserScope
    @Provides
    fun providesContactsService(
        authTokenManager: AuthTokenManager,
        serverUrls: SlyBuildConfig.ServerUrls,
        contactsPersistenceManager: ContactsPersistenceManager,
        addressBookOperationManager: AddressBookOperationManager,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): ContactsService {
        val serverUrl = serverUrls.API_SERVER
        val contactClient = ContactLookupAsyncClientImpl(serverUrl, httpClientFactory)

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
        messageService: MessageService,
        messageCipherService: MessageCipherService,
        groupService: GroupService,
        @UIVisibility uiVisibility: Observable<Boolean>,
        uiEventService: UIEventService,
        relayClock: RelayClock
    ): MessageProcessor = MessageProcessorImpl(
        userData.userId,
        contactsService,
        messageService,
        messageCipherService,
        groupService,
        relayClock,
        uiVisibility,
        uiEventService.events
    )

    @UserScope
    @Provides
    fun providesMessageReceiver(
        messageProcessor: MessageProcessor,
        packageQueuePersistenceManager: PackageQueuePersistenceManager,
        messageCipherService: MessageCipherService,
        eventLogService: EventLogService
    ): MessageReceiver = MessageReceiverImpl(
        messageProcessor,
        packageQueuePersistenceManager,
        messageCipherService,
        eventLogService
    )

    @UserScope
    @Provides
    fun providesMessageSender(
        relayClientManager: RelayClientManager,
        messageCipherService: MessageCipherService,
        messageQueuePersistenceManager: MessageQueuePersistenceManager,
        messageService: MessageService,
        relayClock: RelayClock
    ): MessageSender =
        MessageSenderImpl(
            messageCipherService,
            relayClientManager,
            messageQueuePersistenceManager,
            relayClock,
            messageService.messageUpdates
        )

    @UserScope
    @Provides
    fun providesMessengerService(
        contactsService: ContactsService,
        messageService: MessageService,
        groupService: GroupService,
        relayClientManager: RelayClientManager,
        messageReceiver: MessageReceiver,
        messageSender: MessageSender,
        relayClock: RelayClock,
        userLoginData: UserData
    ): MessengerService =
        MessengerServiceImpl(
            contactsService,
            messageService,
            groupService,
            relayClientManager,
            messageSender,
            messageReceiver,
            relayClock,
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
        messageService: MessageService,
        uiEventService: UIEventService,
        platformNotificationService: PlatformNotificationService,
        userConfigService: UserConfigService,
        @UIVisibility uiVisibility: Observable<Boolean>,
        scheduler: Scheduler
    ): NotifierService {
        return NotifierServiceImpl(
            uiEventService.events,
            messageService.conversationInfoUpdates,
            uiVisibility,
            scheduler,
            400,
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
        @SlyHttp httpClientFactory: HttpClientFactory,
        eventLogService: EventLogService
    ): MessageCipherService {
        val preKeyClient = HttpPreKeyClient(serverUrls.API_SERVER, httpClientFactory.create())
        return MessageCipherServiceImpl(
            userData.userId,
            authTokenManager,
            preKeyClient,
            signalProtocolStore,
            eventLogService
        )
    }

    @UserScope
    @Provides
    fun providesPreKeyManager(
        application: SlyApplication,
        serverUrls: ServerUrls,
        keyVault: KeyVault,
        preKeyPersistenceManager: PreKeyPersistenceManager,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager
    ): PreKeyManager {
        val serverUrl = serverUrls.API_SERVER
        val preKeyAsyncClient = PreKeyAsyncClient(serverUrl, httpClientFactory)

        return PreKeyManagerImpl(
            application.networkAvailable,
            application.installationData.registrationId,
            keyVault,
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
        promiseTimerFactory: PromiseTimerFactory,
        scheduler: Scheduler
    ): AuthTokenManager =
        AuthTokenManagerImpl(userLoginData.address, tokenProvider, promiseTimerFactory, scheduler)

    @UserScope
    @Provides
    fun providesConfigService(
        userPaths: UserPaths,
        accountLocalInfo: AccountLocalInfo,
        defaultUserConfig: UserConfig
    ): UserConfigService {
        val fileStorage = FileConfigStorage(userPaths.configPath)
        val storage = if (SlyBuildConfig.ENABLE_CONFIG_ENCRYPTION) {
            //can't use Cipher*Stream since we're using bouncycastle to properly support stuff
            val derivedKeySpec = accountLocalInfo.getDerivedKeySpec(LocalDerivedKeyType.GENERIC)
            CipherConfigStorageFilter(derivedKeySpec, fileStorage)
        }
        else
            fileStorage

        val backend = JsonConfigBackend("user-config", storage)
        return UserConfigService(backend, defaultUserConfig)
    }

    @UserScope
    @Provides
    fun providesGroupService(
        groupPersistenceManager: GroupPersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager,
        addressBookOperationManager: AddressBookOperationManager,
        messageService: MessageService
    ): GroupService =
        GroupServiceImpl(
            groupPersistenceManager,
            contactsPersistenceManager,
            addressBookOperationManager,
            messageService
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
            accountInfoManager.accountInfo,
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
            TimeUnit.SECONDS.toMillis(5)
        )

    @UserScope
    @Provides
    fun providesSessionDataManager(
        sessionDataPersistenceManager: SessionDataPersistenceManager
    ): SessionDataManager =
        SessionDataManagerImpl(sessionDataPersistenceManager)

    @UserScope
    @Provides
    fun providesAccountParamsManager(
        accountLocalInfoPersistenceManager: AccountLocalInfoPersistenceManager
    ): AccountLocalInfoManager =
        AccountLocalInfoManagerImpl(accountLocalInfoPersistenceManager)

    @UserScope
    @Provides
    fun providesMutualContactNotifier(
        contactsService: ContactsService,
        messengerService: MessengerService
    ): MutualContactNotifier {
        return MutualContactNotifierImpl(contactsService.contactEvents, messengerService)
    }

    @UserScope
    @Provides
    fun providesConversationWatcher(
        uiEventService: UIEventService,
        @UIVisibility uiVisibility: Observable<Boolean>,
        messageService: MessageService
    ): ConversationWatcher {
        return ConversationWatcherImpl(uiEventService.events, uiVisibility, messageService)
    }

    @UserScope
    @Provides
    fun providesAddressBookSyncWatcher(
        addressBookOperationManager: AddressBookOperationManager,
        messengerService: MessengerService
    ): AddressBookSyncWatcher {
        return AddressBookSyncWatcherImpl(addressBookOperationManager.syncEvents, messengerService)
    }

    @UserScope
    @Provides
    fun providesMessageService(
        messagePersistenceManager: MessagePersistenceManager
    ): MessageService {
        return MessageServiceImpl(messagePersistenceManager)
    }

    @UserScope
    @Provides
    fun providersMessageExpirationWatcher(
        scheduler: Scheduler,
        messageService: MessageService,
        messengerService: MessengerService
    ): MessageExpirationWatcher {
        val rxTimerFactory = RxTimerFactory(Schedulers.computation())
        return MessageExpirationWatcherImpl(
            scheduler,
            rxTimerFactory,
            messageService,
            messengerService
        )
    }

    @UserScope
    @Provides
    fun providesMessageReadWatcher(
        messageService: MessageService,
        messengerService: MessengerService
    ): MessageReadWatcher {
        return MessageReadWatcherImpl(messageService, messengerService)
    }

    @UserScope
    @Provides
    fun providesMessageDeletionWatcher(
        messageService: MessageService,
        messengerService: MessengerService
    ): MessageDeletionWatcher {
        return MessageDeletionWatcherImpl(messageService.messageUpdates, messengerService)
    }

    @UserScope
    @Provides
    fun providesEventLogService(
        eventLog: EventLog
    ): EventLogService {
        return EventLogServiceImpl(eventLog)
    }

    @UserScope
    @Provides
    fun providesGroupEventLoggerWatcher(
        groupService: GroupService,
        eventLogService: EventLogService
    ): GroupEventLoggerWatcher {
        return GroupEventLoggerWatcherImpl(groupService.groupEvents, eventLogService)
    }

    @UserScope
    @Provides
    @EmptyReadMessageQueue
    fun providesEmptyReadMessageQueue(
        messageReceiver: MessageReceiver
    ): Observable<Unit> {
        return messageReceiver.queueIsEmpty
    }

    @UserScope
    @Provides
    fun providesDownloader(
        serverUrls: ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager,
        downloadPersistenceManager: DownloadPersistenceManager,
        mainScheduler: Scheduler
    ): Downloader {
        val operations = DownloadOperationsImpl(
            authTokenManager,
            StorageClientFactoryImpl(serverUrls.API_SERVER, serverUrls.FILE_SERVER, httpClientFactory),
            Schedulers.io()
        )

        return DownloaderImpl(
            0,
            downloadPersistenceManager,
            operations,
            Schedulers.computation(),
            mainScheduler,
            false
        )
    }

    @UserScope
    @Provides
    fun providesUploader(
        serverUrls: ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager,
        uploadPersistenceManager: UploadPersistenceManager,
        mainScheduler: Scheduler,
        keyVault: KeyVault
    ): Uploader {
        val operations = UploadOperationsImpl(
            authTokenManager,
            UploadClientFactoryImpl(serverUrls.API_SERVER, serverUrls.FILE_SERVER, httpClientFactory),
            keyVault,
            Schedulers.io()
        )

        return UploaderImpl(
            0,
            uploadPersistenceManager,
            operations,
            Schedulers.computation(),
            mainScheduler,
            false
        )
    }

    @UserScope
    @Provides
    fun providesTransferManager(
        uploader: Uploader,
        downloader: Downloader,
        @NetworkStatus networkStatus: Observable<Boolean>
    ): TransferManager {
        val manager = TransferManagerImpl(
            uploader,
            downloader,
            networkStatus
        )

        manager.options = TransferOptions(10, 10)

        return manager
    }

    @UserScope
    @Provides
    fun providesStorageService(
        serverUrls: ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        authTokenManager: AuthTokenManager,
        fileListPersistenceManager: FileListPersistenceManager,
        transferManager: TransferManager,
        fileAccess: PlatformFileAccess,
        @NetworkStatus networkStatus: Observable<Boolean>,
        keyVault: KeyVault
    ): StorageService {
        val storageClient = StorageAsyncClientImpl(serverUrls.API_SERVER, serverUrls.FILE_SERVER, httpClientFactory)
        val syncJobFactory = StorageSyncJobFactoryImpl(keyVault, fileListPersistenceManager, storageClient)

        return StorageServiceImpl(
            authTokenManager,
            storageClient,
            fileListPersistenceManager,
            syncJobFactory,
            transferManager,
            fileAccess,
            networkStatus
        )
    }
}
