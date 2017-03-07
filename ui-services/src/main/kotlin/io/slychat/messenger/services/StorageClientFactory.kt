package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.storage.StorageClient

interface StorageClientFactory {
    fun create(): StorageClient
}