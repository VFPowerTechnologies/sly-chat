package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
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

    override fun getFileList(userCredentials: UserCredentials, sinceVersion: Long): Promise<FileListResponse, Exception> = task {
        newClient().getFileList(userCredentials, sinceVersion)
    }

    override fun update(userCredentials: UserCredentials, request: UpdateRequest): Promise<UpdateResponse, Exception> = task {
        newClient().update(userCredentials, request)
    }
}