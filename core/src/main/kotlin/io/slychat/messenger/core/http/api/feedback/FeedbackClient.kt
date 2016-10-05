package io.slychat.messenger.core.http.api.feedback

import io.slychat.messenger.core.UserCredentials

interface FeedbackClient {
    fun submitFeedback(userCredentials: UserCredentials, request: FeedbackRequest)
}

