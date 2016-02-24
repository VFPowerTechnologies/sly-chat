package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.relay.base.RelayConnector
import com.vfpowertech.keytap.core.relay.base.netty.NettyRelayConnector
import dagger.Module
import dagger.Provides

@Module
class RelayModule {
    @get:Provides
    val providesRelayConnector: RelayConnector = NettyRelayConnector()
}