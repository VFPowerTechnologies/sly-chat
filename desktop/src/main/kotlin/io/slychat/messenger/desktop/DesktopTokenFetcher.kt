package io.slychat.messenger.desktop

import io.slychat.messenger.services.TokenFetcher
import nl.komponents.kovenant.Promise

class DesktopTokenFetcher : TokenFetcher {
    override fun canRetry(exception: Exception): Boolean {
        return false
    }

    override fun fetch(): Promise<String, Exception> {
        return Promise.ofFail(RuntimeException("No TokenFetcher impl for desktop"))

    }

    override fun isInterestingException(exception: Exception): Boolean {
        return false
    }
}