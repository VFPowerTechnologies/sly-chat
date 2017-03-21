package io.slychat.messenger.core.http.api.upload

import io.slychat.messenger.core.UserCredentials
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

interface UploadClient {
    fun getUpload(userCredentials : UserCredentials, uploadId: String): GetUploadResponse

    fun getUploads(userCredentials: UserCredentials): GetUploadsResponse

    fun newUpload(userCredentials: UserCredentials, request: NewUploadRequest): NewUploadResponse

    //if upload is only a single part, there's no need to call completeUpload
    fun uploadPart(userCredentials: UserCredentials, uploadId: String, partN: Int, size: Long, inputStream: InputStream, isCancelled: AtomicBoolean): UploadPartCompleteResponse

    fun completeUpload(userCredentials: UserCredentials, uploadId: String)

    //throws UploadInProgressException if an upload is still active
    fun cancel(userCredentials: UserCredentials, uploadId: String)
}

