package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.upload.UploadClient

interface UploadClientFactory {
    fun create(): UploadClient
}