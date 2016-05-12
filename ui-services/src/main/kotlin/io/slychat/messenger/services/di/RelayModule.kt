package io.slychat.messenger.services.di

import io.slychat.messenger.core.relay.base.RelayConnector
import io.slychat.messenger.core.relay.base.netty.NettyRelayConnector
import dagger.Module
import dagger.Provides

@Module
class RelayModule {
    @get:Provides
    val providesRelayConnector: RelayConnector = NettyRelayConnector()
}