package io.slychat.messenger.services

import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.relay.RelayClient
import io.slychat.messenger.core.relay.base.RelayConnector
import rx.Scheduler

class RelayClientFactory(
    private val scheduler: Scheduler,
    private val relayConnector: RelayConnector,
    private val serverUrls: BuildConfig.ServerUrls,
    private val sslConfigurator: SSLConfigurator
) {
    fun createClient(userCredentials: UserCredentials): RelayClient {
        return RelayClient(relayConnector, scheduler, serverUrls.RELAY_SERVER, userCredentials, sslConfigurator)
    }
}