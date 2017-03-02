package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.api.UpdateMetadataResponse
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class StorageAsyncClientImpl(
    private val serverUrl: String,
    private val fileServerBaseUrl: String,
    private val factory: HttpClientFactory
) : StorageAsyncClient {
    private fun newClient() = StorageClientImpl(serverUrl, fileServerBaseUrl, factory.create())

    override fun getQuota(userCredentials: UserCredentials): Promise<Quota, Exception> = task {
        newClient().getQuota(userCredentials)
    }

    override fun getFileList(userCredentials: UserCredentials, sinceVersion: Int): Promise<FileListResponse, Exception> = task {
        newClient().getFileList(userCredentials, sinceVersion)
    }

    override fun updateMetadata(userCredentials: UserCredentials, fileId: String, newMetadata: ByteArray): Promise<UpdateMetadataResponse, Exception> = task {
        newClient().updateMetadata(userCredentials, fileId, newMetadata)
    }
}