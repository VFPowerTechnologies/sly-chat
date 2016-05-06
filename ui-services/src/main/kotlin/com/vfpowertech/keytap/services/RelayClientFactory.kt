package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.core.relay.UserCredentials
import com.vfpowertech.keytap.core.relay.base.RelayConnector
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