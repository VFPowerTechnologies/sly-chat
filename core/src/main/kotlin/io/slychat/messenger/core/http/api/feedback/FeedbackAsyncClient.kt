package io.slychat.messenger.core.http.api.feedback

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface FeedbackAsyncClient {
    fun submitFeedback(userCredentials: UserCredentials, request: FeedbackRequest): Promise<Unit, Exception>
}