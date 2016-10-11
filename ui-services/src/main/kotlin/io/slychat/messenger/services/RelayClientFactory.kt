package io.slychat.messenger.services

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.relay.RelayClient

interface RelayClientFactory {
    fun createClient(userCredentials: UserCredentials): RelayClient
}