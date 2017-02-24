package io.slychat.messenger.core.http.api.upload

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef
import java.io.InputStream

class UploadClientImpl(
    private val serverBaseUrl: String,
    private val fileServerBaseUrl: String,
    private val httpClient: HttpClient
) : UploadClient {
    override fun getUploads(userCredentials: UserCredentials): GetUploadsResponse {
        TODO()
    }

    override fun newUpload(userCredentials: UserCredentials, request: NewUploadRequest): NewUploadResponse {
        val url = "$serverBaseUrl/v1/upload"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    override fun uploadPart(userCredentials: UserCredentials, uploadId: String, partN: Int, inputStream: InputStream): UploadCompleteResponse {
        TODO()
    }

    override fun completeUpload(userCredentials: UserCredentials, uploadId: String): UploadCompleteResponse {
        val url = "$serverBaseUrl/v1/upload/$uploadId";
        return apiPostRequest(httpClient, url, userCredentials, mapOf<String, String>(), typeRef())
    }
}