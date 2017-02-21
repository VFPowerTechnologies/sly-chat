package io.slychat.messenger.core.http.api

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.typeRef

class StorageClientImpl(private val serverBaseUrl: String, private val httpClient: HttpClient) : StorageClient {
    override fun getQuota(userCredentials: UserCredentials): Quota {
        val url = "$serverBaseUrl/v1/storage/quota"
        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef())
    }

    override fun getFileList(userCredentials: UserCredentials, sinceVersion: Int): FileListResponse {
        TODO()
    }

    override fun updateMetadata(userCredentials: UserCredentials, newMetadata: ByteArray): UpdateMetadataResponse {
        TODO()
    }

    override fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): AcceptShareResponse {
        TODO()
    }
}