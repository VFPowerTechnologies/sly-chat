package io.slychat.messenger.core.http.api.feedback

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class FeedbackClientImpl(
    private val serverBaseUrl: String,
    private val httpClient: HttpClient
) : FeedbackClient {
    override fun submitFeedback(userCredentials: UserCredentials, request: FeedbackRequest) {
        val url = "$serverBaseUrl/v1/feedback"

        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }
}