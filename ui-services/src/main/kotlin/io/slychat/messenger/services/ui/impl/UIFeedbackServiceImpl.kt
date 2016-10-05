package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.http.api.feedback.FeedbackAsyncClient
import io.slychat.messenger.core.http.api.feedback.FeedbackRequest
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIFeedbackService
import nl.komponents.kovenant.Promise
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

class UIFeedbackServiceImpl(
    userSessionAvailable: Observable<UserComponent?>,
    private val feedbackAsyncClient: FeedbackAsyncClient
) : UIFeedbackService {
    private val log = LoggerFactory.getLogger(javaClass)

    private var authTokenManager: AuthTokenManager? = null

    private var subscription: Subscription? = null

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun getAuthTokenManagerOrThrow(): AuthTokenManager {
        return authTokenManager ?: error("No user session has been established")
    }

    override fun submitFeedback(feedbackText: String): Promise<Unit, Exception> {
        log.debug("Submitting feedback")

        return getAuthTokenManagerOrThrow().bind {
            feedbackAsyncClient.submitFeedback(it, FeedbackRequest(feedbackText))
        }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent != null) {
            authTokenManager = userComponent.authTokenManager
        }
        else {
            subscription?.unsubscribe()
            subscription = null
        }
    }
}