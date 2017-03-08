package io.slychat.messenger.core.http.api.upload

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.MultipartPart
import io.slychat.messenger.core.http.api.*
import io.slychat.messenger.core.typeRef
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class UploadClientImpl(
    private val serverBaseUrl: String,
    private val fileServerBaseUrl: String,
    private val httpClient: HttpClient
) : UploadClient {
    override fun getUpload(userCredentials: UserCredentials, uploadId: String): UploadInfo? {
        val url = "$serverBaseUrl/v1/upload/$uploadId"

        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef())
    }

    override fun getUploads(userCredentials: UserCredentials): GetUploadsResponse {
        val url = "$serverBaseUrl/v1/upload"

        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef())
    }

    override fun newUpload(userCredentials: UserCredentials, request: NewUploadRequest): NewUploadResponse {
        val url = "$serverBaseUrl/v1/upload"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    override fun uploadPart(userCredentials: UserCredentials, uploadId: String, partN: Int, size: Long, inputStream: InputStream, isCancelled: AtomicBoolean, filterStream: ((OutputStream) -> OutputStream)?): UploadPartCompleteResponse {
        val url = "$fileServerBaseUrl/v1/upload/$uploadId/$partN"

        val dataPart = MultipartPart.Data("data", size, inputStream)

        val resp = httpClient.upload(
            url,
            userCredentialsToHeaders(userCredentials),
            listOf(dataPart),
            isCancelled,
            filterStream
        )

        return valueFromApi(resp, typeRef())
    }

    override fun completeUpload(userCredentials: UserCredentials, uploadId: String) {
        val url = "$serverBaseUrl/v1/upload/$uploadId"
        apiPostRequest(httpClient, url, userCredentials, mapOf<String, String>(), typeRef<ApiResult<EmptyResponse>>())
    }

    override fun delete(userCredentials: UserCredentials, uploadId: String) {
        TODO()
    }
}