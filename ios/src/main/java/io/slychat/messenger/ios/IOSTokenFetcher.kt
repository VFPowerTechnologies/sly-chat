package io.slychat.messenger.ios

import io.slychat.messenger.services.DeviceTokens
import io.slychat.messenger.services.TokenFetcher
import nl.komponents.kovenant.Promise

class IOSTokenFetcher(private val app: IOSApp) : TokenFetcher {
    override fun canRetry(exception: Exception): Boolean {
        return true
    }

    override fun fetch(): Promise<DeviceTokens?, Exception> {
        return app.registerForNotifications()
    }

    override fun isInterestingException(exception: Exception): Boolean {
        return true
    }
}