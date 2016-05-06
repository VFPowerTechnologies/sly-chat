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
    val providesAccountInfo: AccountInfo
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
        preKeyPersistenceManager: PreKeyPersistenceManager,
        authTokenManager: AuthTokenManager
    ): PreKeyManager =
        PreKeyManager(application, serverUrls.API_SERVER, userLoginData, preKeyPersistenceManager, authTokenManager)

    @UserScope
    @Provides
    fun providesOfflineMessageManager(
        application: KeyTapApplication,
        serverUrls: ServerUrls,
        messengerService: MessengerService,
        authTokenManager: AuthTokenManager
    ): OfflineMessageManager =
        OfflineMessageManager(application, serverUrls.API_SERVER, messengerService, authTokenManager)

    @UserScope
    @Provides
    fun providesContactSyncManager(
        application: KeyTapApplication,
        userLoginData: UserLoginData,
        accountInfo: AccountInfo,
        serverUrls: ServerUrls,
        platformContacts: PlatformContacts,
        contactsPersistenceManager: ContactsPersistenceManager,
        authTokenManager: AuthTokenManager
    ): ContactSyncManager =
        ContactSyncManager(
            application,
            userLoginData,
            accountInfo,
            serverUrls.API_SERVER,
            platformContacts,
            contactsPersistenceManager,
            authTokenManager
        )

    @UserScope
    @Provides
    fun providesTokenProvider(
        application: KeyTapApplication,
        userLoginData: UserLoginData,
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
        tokenProvider: TokenProvider
    ): AuthTokenManager =
        AuthTokenManager(tokenProvider)
}
