package io.slychat.messenger.core.http.api.upload

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.MultipartPart
import io.slychat.messenger.core.http.api.*
import io.slychat.messenger.core.typeRef
import java.io.InputStream
import java.io.OutputStream

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

    override fun uploadPart(userCredentials: UserCredentials, uploadId: String, partN: Int, size: Long, inputStream: InputStream, filterStream: ((OutputStream) -> OutputStream)?): UploadPartCompleteResponse {
        val url = "$fileServerBaseUrl/v1/upload/$uploadId/$partN"

        val dataPart = MultipartPart.Data("data", size, inputStream)

        return inputStream.use {
            val resp = httpClient.upload(
                url,
                userCredentialsToHeaders(userCredentials),
                listOf(dataPart),
                filterStream
            )

            valueFromApi(resp, typeRef())
        }
    }

    override fun completeUpload(userCredentials: UserCredentials, uploadId: String) {
        val url = "$serverBaseUrl/v1/upload/$uploadId"
        apiPostRequest(httpClient, url, userCredentials, mapOf<String, String>(), typeRef<ApiResult<EmptyResponse>>())
    }
}