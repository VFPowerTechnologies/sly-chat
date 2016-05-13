package io.slychat.messenger.services

import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.relay.RelayClient
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.relay.base.RelayConnector
import rx.Scheduler

class RelayClientFactory(
    val scheduler: Scheduler,
    val relayConnector: RelayConnector,
    val serverUrls: BuildConfig.ServerUrls
) {
    fun createClient(userCredentials: UserCredentials): RelayClient {
        return RelayClient(relayConnector, scheduler, serverUrls.RELAY_SERVER, userCredentials)
    }
}