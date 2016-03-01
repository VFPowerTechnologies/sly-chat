package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig.ServerUrls
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.relay.base.RelayConnector
import com.vfpowertech.keytap.services.UserLoginData
import com.vfpowertech.keytap.services.RelayClientManager
import dagger.Module
import dagger.Provides
import rx.Scheduler

@Module
class UserModule(
    @get:UserScope
    @get:Provides
    val providesUserLoginData: UserLoginData
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
}