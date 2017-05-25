package io.slychat.messenger.services

import rx.Observable

/** Provides a shared method for the application to request push notification token refreshes, as well as being notified when such events occur. */
interface TokenFetchService {
    val tokenUpdates: Observable<DeviceTokens?>

    fun refresh()
}