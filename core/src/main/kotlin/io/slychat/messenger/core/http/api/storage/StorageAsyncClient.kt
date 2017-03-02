package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface StorageAsyncClient {
    fun getQuota(userCredentials: UserCredentials): Promise<Quota, Exception>

    fun getFileList(userCredentials: UserCredentials, sinceVersion: Int): Promise<FileListResponse, Exception>

    fun update(userCredentials: UserCredentials, request: UpdateRequest): Promise<UpdateResponse, Exception>
}