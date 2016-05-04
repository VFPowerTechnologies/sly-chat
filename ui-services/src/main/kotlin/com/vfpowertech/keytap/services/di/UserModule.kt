package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig.ServerUrls
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.MessagePersistenceManager
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.relay.base.RelayConnector
import com.vfpowertech.keytap.services.*
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
    @Provides
    fun provideRelayClient(
        userLoginData: UserLoginData,
        scheduler: Scheduler,
        relayConnector: RelayConnector,
        serverUrls: ServerUrls
    ): RelayClient {
        val credentials = UserCredentials(userLoginData.address, userLoginData.authToken!!)
        return RelayClient(relayConnector, scheduler, serverUrls.RELAY_SERVER, credentials)
    }

    @UserScope
    @Provides
    fun providesRelayClientManager(
        scheduler: Scheduler,
        userComponent: UserComponent
    ): RelayClientManager =
        RelayClientManager(scheduler, userComponent)

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
        userLoginData: UserLoginData,
        serverUrls: ServerUrls,
        signalProtocolStore: SignalProtocolStore
    ): MessageCipherService =
        MessageCipherService(userLoginData, signalProtocolStore, serverUrls)

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
}