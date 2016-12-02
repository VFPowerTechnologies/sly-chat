package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.relay.base.RelayConnector
import io.slychat.messenger.core.relay.base.java.JavaRelayConnector

@Module
class RelayModule {
    @Provides
    fun providesRelayConnector(): RelayConnector = JavaRelayConnector()
}