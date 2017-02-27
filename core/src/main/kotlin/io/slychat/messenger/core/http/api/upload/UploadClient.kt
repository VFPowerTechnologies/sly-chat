package io.slychat.messenger.core.http.api.upload

import io.slychat.messenger.core.UserCredentials
import java.io.InputStream
import java.io.OutputStream

interface UploadClient {
    fun getUploads(userCredentials: UserCredentials): GetUploadsResponse

    fun newUpload(userCredentials: UserCredentials, request: NewUploadRequest): NewUploadResponse

    //if upload is only a single part, there's no need to call completeUpload
    fun uploadPart(userCredentials: UserCredentials, uploadId: String, partN: Int, size: Long, inputStream: InputStream, filterStream: ((OutputStream) -> OutputStream)?): UploadPartCompleteResponse

    fun completeUpload(userCredentials: UserCredentials, uploadId: String)
}

