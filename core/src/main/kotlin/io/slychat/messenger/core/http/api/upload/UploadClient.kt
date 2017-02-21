package io.slychat.messenger.core.http.api.upload

import io.slychat.messenger.core.UserCredentials
import java.io.InputStream

interface UploadClient {
    fun getUploads(userCredentials: UserCredentials): UploadsResponse

    fun newUpload(userCredentials: UserCredentials, request: NewUploadRequest): NewUploadResponse

    //if upload is only a single part, there's no need to call completeUpload
    fun uploadPart(userCredentials: UserCredentials, uploadId: String, partN: Int, inputStream: InputStream): UploadCompleteResponse

    fun completeUpload(userCredentials: UserCredentials, uploadId: String): UploadCompleteResponse
}

