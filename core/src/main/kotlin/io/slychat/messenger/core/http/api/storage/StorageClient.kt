package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.api.share.AcceptShareRequest
import io.slychat.messenger.core.http.api.share.AcceptShareResponse

interface StorageClient {
    fun getFileList(userCredentials: UserCredentials, sinceVersion: Long): FileListResponse

    fun getFileInfo(userCredentials : UserCredentials, fileId: String): GetFileInfoResponse

    fun update(userCredentials: UserCredentials, request: UpdateRequest): UpdateResponse

    //if an error occurs, ApiException is thrown
    //if the file is missing, null is returned
    fun downloadFile(userCredentials: UserCredentials, fileId: String): DownloadFileResponse?
}
