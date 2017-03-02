package io.slychat.messenger.core.http.api.storage

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.HttpResponse
import io.slychat.messenger.core.http.api.*
import io.slychat.messenger.core.typeRef

class StorageClientImpl(
    private val serverBaseUrl: String,
    private val fileServerBaseUrl: String,
    private val httpClient: HttpClient
) : StorageClient {
    override fun getQuota(userCredentials: UserCredentials): Quota {
        val url = "$serverBaseUrl/v1/storage/quota"
        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef())
    }

    override fun getFileList(userCredentials: UserCredentials, sinceVersion: Long): FileListResponse {
        val url = "$serverBaseUrl/v1/storage"

        return apiGetRequest(httpClient, url, userCredentials, listOf("v" to sinceVersion.toString()), typeRef())
    }

    override fun getFileInfo(userCredentials: UserCredentials, fileId: String): GetFileInfoResponse {
        val url = "$serverBaseUrl/v1/storage/$fileId"

        return apiGetRequest(httpClient, url, userCredentials, emptyList(), typeRef())
    }

    override fun update(userCredentials: UserCredentials, request: UpdateRequest): UpdateResponse {
        val url = "$serverBaseUrl/v1/storage"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    override fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): AcceptShareResponse {
        TODO()
    }

    override fun downloadFile(userCredentials: UserCredentials, fileId: String): DownloadFileResponse? {
        val url = "$fileServerBaseUrl/v1/storage/$fileId"

        val resp = httpClient.download(url, userCredentialsToHeaders(userCredentials))
        if (!resp.isSuccess) {
            if (resp.code == 404)
                return null

            val data = resp.body.use { it.reader().readText() }
            val response = HttpResponse(resp.code, resp.headers, data)
            throwApiException<Any>(response, typeRef())
        }

        val contentLengthHeaders = resp.headers["content-length"]
        if (contentLengthHeaders == null || contentLengthHeaders.isEmpty()) {
            resp.body.close()
            throw RuntimeException("Content-Length header missing")
        }

        val contentLength = contentLengthHeaders.first().toLong()

        return DownloadFileResponse(contentLength, resp.body)
    }
}