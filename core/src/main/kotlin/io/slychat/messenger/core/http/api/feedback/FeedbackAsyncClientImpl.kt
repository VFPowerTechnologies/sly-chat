package io.slychat.messenger.core.http.api.feedback

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class FeedbackAsyncClientImpl(private val serverUrl: String, private val factory: HttpClientFactory) : FeedbackAsyncClient {
    private fun newClient() = FeedbackClientImpl(serverUrl, factory.create())

    override fun submitFeedback(userCredentials: UserCredentials, request: FeedbackRequest): Promise<Unit, Exception> = task {
        newClient().submitFeedback(userCredentials, request)
    }
}