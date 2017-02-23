package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.api.AcceptShareRequest
import io.slychat.messenger.core.http.api.AcceptShareResponse
import io.slychat.messenger.core.http.api.UpdateMetadataResponse

interface StorageClient {
    fun getQuota(userCredentials: UserCredentials): Quota

    fun getFileList(userCredentials: UserCredentials, sinceVersion: Int): FileListResponse

    fun updateMetadata(userCredentials : UserCredentials, newMetadata: ByteArray): UpdateMetadataResponse

    //may no longer be present if the original owner deleted it
    fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): AcceptShareResponse
}