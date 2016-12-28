package io.slychat.messenger.ios

import io.slychat.messenger.services.TokenFetcher
import nl.komponents.kovenant.Promise

class IOSTokenFetcher(private val notificationRegisterer: NotificationRegisterer) : TokenFetcher {
    override fun canRetry(exception: Exception): Boolean {
        return true
    }

    override fun fetch(): Promise<String?, Exception> {
        return notificationRegisterer.registerForNotifications()
    }

    override fun isInterestingException(exception: Exception): Boolean {
        return true
    }
}