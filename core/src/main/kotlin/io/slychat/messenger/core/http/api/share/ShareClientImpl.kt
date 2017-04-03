package io.slychat.messenger.core.http.api.share

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class ShareClientImpl(private val serverBaseUrl: String, private val httpClient: HttpClient) : ShareClient {
    override fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): AcceptShareResponse {
        val url = "$serverBaseUrl/v1/share"

        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }
}