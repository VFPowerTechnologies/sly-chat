package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface StorageAsyncClient {
    fun getFileList(userCredentials: UserCredentials, sinceVersion: Long): Promise<FileListResponse, Exception>

    fun update(userCredentials: UserCredentials, request: UpdateRequest): Promise<UpdateResponse, Exception>
}