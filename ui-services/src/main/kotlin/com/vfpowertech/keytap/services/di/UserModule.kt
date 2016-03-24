package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig.ServerUrls
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.MessagePersistenceManager
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.relay.base.RelayConnector
import com.vfpowertech.keytap.services.*
import dagger.Module
import dagger.Provides
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
        val credentials = UserCredentials(userLoginData.username, userLoginData.authToken!!)
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
    messagePersistenceManager: MessagePersistenceManager,
    contactsPersistenceManager: ContactsPersistenceManager,
    relayClientManager: RelayClientManager
    ): MessengerService =
        MessengerService(messagePersistenceManager, contactsPersistenceManager, relayClientManager)

    @UserScope
    @Provides
    fun providersUserPaths(
        userLoginData: UserLoginData,
        userPathsGenerator: UserPathsGenerator
    ): UserPaths =
        userPathsGenerator.getPaths(userLoginData.username)
}