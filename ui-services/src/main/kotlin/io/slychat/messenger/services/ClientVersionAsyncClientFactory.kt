package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.versioncheck.ClientVersionAsyncClient

interface ClientVersionAsyncClientFactory {
    fun create(): ClientVersionAsyncClient
}