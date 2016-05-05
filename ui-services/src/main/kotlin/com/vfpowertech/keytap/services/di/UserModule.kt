package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig.ServerUrls
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.MessagePersistenceManager
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import com.vfpowertech.keytap.core.relay.base.RelayConnector
import com.vfpowertech.keytap.services.*
import com.vfpowertech.keytap.services.auth.AuthTokenManager
import com.vfpowertech.keytap.services.auth.AuthenticationServiceTokenProvider
import com.vfpowertech.keytap.services.auth.TokenProvider
import com.vfpowertech.keytap.services.crypto.MessageCipherService
import com.vfpowertech.keytap.services.ui.UIEventService
import dagger.Module
import dagger.Provides
import org.whispersystems.libsignal.state.SignalProtocolStore
import rx.Scheduler

@Module
class UserModule(
    @get:UserScope
    @get:Provides
    val providesUserLoginData: UserLoginData,
    @get:UserScope
    @get:Provides
    val providersAccountInfo: AccountInfo
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
        userComponent: UserComponent,
        relayClientFactory: RelayClientFactory
    ): RelayClientManager =
        RelayClientManager(scheduler, userComponent, relayClientFactory)

    @UserScope
    @Provides
    fun providesMessengerService(
        application: KeyTapApplication,
        scheduler: Scheduler,
        messagePersistenceManager: MessagePersistenceManager,
        contactsPersistenceManager: ContactsPersistenceManager,
        relayClientManager: RelayClientManager,
        messageCipherService: MessageCipherService,
        userLoginData: UserLoginData
    ): MessengerService =
        MessengerService(
            application,
            scheduler,
            messagePersistenceManager,
            contactsPersistenceManager,
            relayClientManager,
            messageCipherService,
            userLoginData
        )

    @UserScope
    @Provides
    fun providersUserPaths(
        userLoginData: UserLoginData,
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
        userLoginData: UserLoginData,
        serverUrls: ServerUrls,
        signalProtocolStore: SignalProtocolStore
    ): MessageCipherService =
        MessageCipherService(authTokenManager, userLoginData, signalProtocolStore, serverUrls)

    @UserScope
    @Provides
    fun providesPreKeyManager(
        application: KeyTapApplication,
        serverUrls: ServerUrls,
        userLoginData: UserLoginData,
        preKeyPersistenceManager: PreKeyPersistenceManager
    ): PreKeyManager =
        PreKeyManager(application, serverUrls.API_SERVER, userLoginData, preKeyPersistenceManager)

    @UserScope
    @Provides
    fun providesOfflineMessageManager(
        application: KeyTapApplication,
        userLoginData: UserLoginData,
        serverUrls: ServerUrls,
        messengerService: MessengerService
    ): OfflineMessageManager =
        OfflineMessageManager(application, userLoginData, serverUrls.API_SERVER, messengerService)

    @UserScope
    @Provides
    fun providesContactSyncManager(
        application: KeyTapApplication,
        userLoginData: UserLoginData,
        accountInfo: AccountInfo,
        serverUrls: ServerUrls,
        platformContacts: PlatformContacts,
        contactsPersistenceManager: ContactsPersistenceManager
    ): ContactSyncManager =
        ContactSyncManager(
            application,
            userLoginData,
            accountInfo,
            serverUrls.API_SERVER,
            platformContacts,
            contactsPersistenceManager
        )

    @UserScope
    @Provides
    fun providesTokenProvider(
        application: KeyTapApplication,
        userLoginData: UserLoginData,
        accountInfo: AccountInfo,
        authenticationService: AuthenticationService
    ): TokenProvider =
        AuthenticationServiceTokenProvider(
            application,
            accountInfo,
            userLoginData.keyVault,
            authenticationService
        )

    @UserScope
    @Provides
    fun providesAuthTokenManager(
        tokenProvider: TokenProvider
    ): AuthTokenManager =
        AuthTokenManager(tokenProvider)
}
