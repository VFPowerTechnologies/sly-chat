package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress

interface PushNotificationsManager {
    //unregister using the current token value
    //does nothing if no token is available (shouldn't occur)
    fun unregister(address: SlyAddress)
}
